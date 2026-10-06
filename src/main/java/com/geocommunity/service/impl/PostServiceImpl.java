package com.geocommunity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.common.utils.AfterCommit;
import com.geocommunity.common.utils.CacheVersion;
import com.geocommunity.mq.OutboxService;
import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.exception.BusinessException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import com.geocommunity.dto.PostVO;
import com.geocommunity.entity.Comment;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.PostFavorite;
import com.geocommunity.entity.PostLike;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.CategoryMapper;
import com.geocommunity.mapper.CommentMapper;
import com.geocommunity.mapper.PostFavoriteMapper;
import com.geocommunity.mapper.PostLikeMapper;
import com.geocommunity.mapper.PostMapper;
import com.geocommunity.mapper.UserMapper;
import com.geocommunity.mq.MqEvent;
import com.geocommunity.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class PostServiceImpl implements PostService {

    private static final Logger log = LoggerFactory.getLogger(PostServiceImpl.class);

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private PostFavoriteMapper postFavoriteMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private JsonUtil jsonUtil;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    // ===================================================================
    //  缓存 Key 常量（统一在 RedisKeys 中管理）
    // ===================================================================
    private static final String KEY_LIST = RedisKeys.POST_LIST;
    private static final String KEY_SEARCH = RedisKeys.POST_SEARCH;
    private static final String KEY_DETAIL = RedisKeys.POST_DETAIL;
    private static final String KEY_NULL = RedisKeys.POST_NULL;
    private static final String KEY_LIKED = RedisKeys.POST_LIKED;
    private static final String KEY_FAV = RedisKeys.FAVORITES_USER;
    private static final String KEY_POST_FAV = RedisKeys.POST_FAVORITES;
    private static final String LOCK_DETAIL = RedisKeys.LOCK_DETAIL;
    private static final String KEY_VIEW_COUNT = RedisKeys.POST_VIEW;
    private static final String KEY_HOT_BOARD = RedisKeys.POST_HOT_BOARD;

    // ===================================================================
    //  帖子列表（Cache-Aside 手动缓存）
    // ===================================================================
    //
    //  穿透防护：不缓存空值（列表为空是正常情况，不属穿透）
    //  击穿防护：列表缓存 TTL 短（2min），并发低，不需要锁
    //  雪崩防护：TTL 加随机 0~60 秒
    // ===================================================================

    @Override
    public Page<PostVO> listPosts(Integer categoryId, String sort, int page, int size, Long authorId) {
        if (!Set.of("hot", "latest").contains(sort)) throw new BusinessException(400, "排序无效");
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        // 按作者查询不走缓存（每个人查到的不同）
        if (authorId != null) {
            Page<PostVO> pageParam = new Page<>(page, size);
            pageParam.setOptimizeCountSql(false);
            return postMapper.selectPostList(pageParam, categoryId, sort, authorId);
        }

        String cacheKey = CacheVersion.key(redisTemplate, KEY_LIST + (categoryId == null ? 0 : categoryId) + ":" + sort + ":" + page + ":" + size, "post:lists");

        // 1. 读缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            Page<PostVO> result = jsonUtil.fromJson(cached, new TypeReference<Page<PostVO>>() {});
            if (result != null) {
                return result;
            }
        }

        // 2. 缓存未命中 → 查 DB
        Page<PostVO> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        Page<PostVO> result = postMapper.selectPostList(pageParam, categoryId, sort, null);

        // 3. 写缓存（TTL = 2min + 随机 0~60s 防雪崩）
        int ttl = 120 + ThreadLocalRandom.current().nextInt(60);
        redisTemplate.opsForValue().set(cacheKey, jsonUtil.toJson(result), ttl, TimeUnit.SECONDS);

        return result;
    }

    @Override
    public Page<PostVO> nearbyPosts(Double longitude, Double latitude, Integer radius,
                                    Integer categoryId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        if (longitude == null || latitude == null || !Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90
                || radius == null || radius < 1 || radius > 100) throw new BusinessException(400, "位置或半径无效");
        Page<PostVO> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return postMapper.selectNearbyPosts(pageParam, longitude, latitude, radius, categoryId);
    }

    @Override
    public Page<PostVO> searchPosts(String keyword, Integer categoryId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        if (keyword == null || keyword.isBlank() || keyword.length() > 100) throw new BusinessException(400, "关键词长度须为1至100字");
        String cacheKey = CacheVersion.key(redisTemplate, KEY_SEARCH + keyword + ":" + (categoryId == null ? 0 : categoryId) + ":" + page + ":" + size, "post:lists");

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return jsonUtil.fromJson(cached, new TypeReference<Page<PostVO>>() {});
        }

        Page<PostVO> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        Page<PostVO> result = postMapper.searchPosts(pageParam, keyword, categoryId);

        int ttl = 120 + ThreadLocalRandom.current().nextInt(60);
        redisTemplate.opsForValue().set(cacheKey, jsonUtil.toJson(result), ttl, TimeUnit.SECONDS);

        return result;
    }

    // ===================================================================
    //  帖子详情（带三灾防护）
    // ===================================================================
    //
    //  穿透防护：DB 查不到时，缓存空值标记（post:null:{id}，TTL 5min）
    //  击穿防护：分布式锁（lock:detail:{id}），抢到锁的线程查 DB 写缓存，其他等或回源
    //  雪崩防护：TTL 30min + 随机 0~10min，避免批量过期
    //
    //  双写一致性：更新/删除时先改 DB，再删缓存
    // ===================================================================

    @Override
    public Post getDetail(Long id) {
        String cacheKey = CacheVersion.key(redisTemplate, KEY_DETAIL + id, KEY_DETAIL + id);
        String nullKey = cacheKey.equals(KEY_DETAIL + id) ? KEY_NULL + id : cacheKey + ":null";

        // 1. 查空值标记（穿透保护）
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nullKey))) {
            return null;
        }

        // 2. 读缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            Post post = jsonUtil.fromJson(cached, Post.class);
            if (post != null) {
                incrementViewCount(id);
                return post;
            }
        }

        // 3. 分布式锁 —— 防止缓存击穿（热点 key 过期时大量请求同时打 DB）
        String lockKey = LOCK_DETAIL + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            locked = false;
        }

        if (!locked) {
            // 没抢到锁：休眠 50ms 后尝试从缓存拿（等抢到锁的线程写缓存）
            sleep(50);
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                Post post = jsonUtil.fromJson(cached, Post.class);
                if (post != null) {
                    incrementViewCount(id);
                    return post;
                }
            }
            // 等了还没缓存 → 直接查 DB（降级）
            return queryPostDirectly(id);
        }

        try {
            // 4. 双重检查（防止锁等待期间已被其他线程写入）
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                Post post = jsonUtil.fromJson(cached, Post.class);
                if (post != null) {
                    incrementViewCount(id);
                    return post;
                }
            }

            // 5. 查 DB（JOIN 用户和分类，避免 Controller 额外查询）
            Post post = postMapper.selectPostDetail(id);
            if (post == null || post.getStatus() != 1) {
                // 穿透防护：缓存空值标记，5min 内不再穿透到 DB
                redisTemplate.opsForValue().set(nullKey, "1", 5, TimeUnit.MINUTES);
                return null;
            }

            // 6. 写缓存（TTL = 30min + 随机 0~10min 防雪崩）
            int ttl = 1800 + ThreadLocalRandom.current().nextInt(600);
            redisTemplate.opsForValue().set(cacheKey, jsonUtil.toJson(post), ttl, TimeUnit.SECONDS);

            incrementViewCount(id);
            return post;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ===================================================================
    //  发布 / 编辑 / 删除（先改 DB，再删缓存 — 双写一致性）
    // ===================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Post createPost(Post input, Long userId) {
        User author = userMapper.selectForUpdate(userId);
        if (author == null || !Integer.valueOf(1).equals(author.getStatus())) throw new BusinessException(401, "账号不可用");
        validateCategory(input.getCategoryId());
        Post post = editableFields(input);
        post.setAuthorId(userId); post.setStatus(1);
        post.setLikeCount(0); post.setCommentCount(0); post.setViewCount(0);
        post.setCreatedAt(LocalDateTime.now()); post.setUpdatedAt(LocalDateTime.now());
        postMapper.insert(post);
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .setSql("post_count = post_count + 1"));
        AfterCommit.run(() -> {
            CacheVersion.invalidate(redisTemplate, RedisKeys.USER_CACHE + userId, RedisKeys.USER_CACHE + userId);
            redisTemplate.opsForZSet().addIfAbsent(KEY_HOT_BOARD, String.valueOf(post.getId()), 0);
        });
        evictPostListCache();
        outboxService.enqueue(new MqEvent("NEW_POST", post.getId(), userId, null, post.getTitle()));
        return post;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(Long id, Post input, Long userId) {
        Post existing = requirePost(id);
        if (!existing.getAuthorId().equals(userId)) throw new BusinessException(1008, "无操作权限");
        if (input.getCategoryId() != null) validateCategory(input.getCategoryId());
        Post update = editableFields(input);
        update.setId(id); update.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(update);
        invalidateDetail(id);
        evictPostListCache();
    }

    private Post editableFields(Post input) {
        Post p = new Post();
        p.setTitle(input.getTitle()); p.setContent(input.getContent()); p.setCategoryId(input.getCategoryId());
        p.setImages(input.getImages()); p.setLongitude(input.getLongitude()); p.setLatitude(input.getLatitude());
        return p;
    }

    private void validateCategory(Integer id) {
        if (id == null || categoryMapper.selectForUpdate(id) == null) throw new BusinessException(400, "分类不存在");
    }

    private Post requirePost(Long id) {
        // 同一帖子写操作共享数据库行锁，阻止删除与点赞/评论之间产生悬空关系。
        Post post = postMapper.selectActiveForUpdate(id);
        if (post == null) throw new BusinessException(1003, "帖子不存在");
        return post;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id, Long userId) {
        Post post = requirePost(id);
        if (!post.getAuthorId().equals(userId)) throw new BusinessException(1008, "无操作权限");
        removeLockedPost(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removePost(Long id) {
        Post post = postMapper.selectActiveForUpdate(id);
        return post != null && removeLockedPost(post);
    }

    private boolean removeLockedPost(Post post) {
        if (postMapper.deleteById(post.getId()) != 1) return false;
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, post.getAuthorId())
                .setSql("post_count = GREATEST(CAST(post_count AS SIGNED) - 1, 0)"));
        AfterCommit.run(() -> CacheVersion.invalidate(redisTemplate, RedisKeys.USER_CACHE + post.getAuthorId(), RedisKeys.USER_CACHE + post.getAuthorId()));
        cleanupPostRelations(post.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanupPostRelations(Long postId) {
        List<PostFavorite> favorites = postFavoriteMapper.selectList(new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getPostId, postId));
        postLikeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId));
        postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getPostId, postId));
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, postId));
        invalidateDetail(postId);
        AfterCommit.run(() -> {
            redisTemplate.opsForZSet().remove(KEY_HOT_BOARD, String.valueOf(postId));
            redisTemplate.delete(KEY_LIKED + postId);
            if (favorites != null) for (PostFavorite f : favorites) {
                redisTemplate.opsForSet().remove(KEY_FAV + f.getUserId(), String.valueOf(postId));
            }
            redisTemplate.delete(KEY_POST_FAV + postId);
            redisTemplate.delete(KEY_VIEW_COUNT + postId);
        });
        evictPostListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likePost(Long postId, Long userId) {
        Post post = requirePost(postId);
        PostLike like = new PostLike(); like.setPostId(postId); like.setUserId(userId); like.setCreatedAt(LocalDateTime.now());
        try { postLikeMapper.insert(like); }
        catch (DuplicateKeyException e) { throw new BusinessException(1006, "不能重复操作"); }
        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, postId).setSql("like_count = like_count + 1"));
        invalidateDetail(postId); evictPostListCache();
        AfterCommit.run(() -> redisTemplate.opsForZSet().incrementScore(KEY_HOT_BOARD, String.valueOf(postId), 2));
        outboxService.enqueue(new MqEvent("LIKE", postId, userId, post.getAuthorId(), null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikePost(Long postId, Long userId) {
        requirePost(postId);
        int deleted = postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId).eq(PostLike::getUserId, userId));
        if (deleted != 1) throw new BusinessException(1006, "不能重复操作");
        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, postId)
                .setSql("like_count = GREATEST(CAST(like_count AS SIGNED) - 1, 0)"));
        invalidateDetail(postId); evictPostListCache();
        AfterCommit.run(() -> redisTemplate.opsForZSet().incrementScore(KEY_HOT_BOARD, String.valueOf(postId), -2));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoritePost(Long postId, Long userId) {
        requirePost(postId);
        PostFavorite favorite = new PostFavorite(); favorite.setPostId(postId); favorite.setUserId(userId); favorite.setCreatedAt(LocalDateTime.now());
        try { postFavoriteMapper.insert(favorite); }
        catch (DuplicateKeyException e) { throw new BusinessException(1006, "不能重复操作"); }
        AfterCommit.run(() -> redisTemplate.opsForZSet().incrementScore(KEY_HOT_BOARD, String.valueOf(postId), 4));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavoritePost(Long postId, Long userId) {
        requirePost(postId);
        int deleted = postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getPostId, postId).eq(PostFavorite::getUserId, userId));
        if (deleted != 1) throw new BusinessException(1006, "不能重复操作");
        AfterCommit.run(() -> redisTemplate.opsForZSet().incrementScore(KEY_HOT_BOARD, String.valueOf(postId), -4));
    }

    @Override
    public boolean isLiked(Long postId, Long userId) {
        return postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId).eq(PostLike::getUserId, userId)) > 0;
    }

    @Override
    public boolean isFavorited(Long postId, Long userId) {
        return postFavoriteMapper.selectCount(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getPostId, postId).eq(PostFavorite::getUserId, userId)) > 0;
    }

    private void invalidateDetail(Long id) {
        AfterCommit.run(() -> {
            CacheVersion.invalidate(redisTemplate, KEY_DETAIL + id, KEY_DETAIL + id);
            redisTemplate.delete(KEY_NULL + id);
        });
    }

    // ===================================================================
    //  缓存辅助方法
    // ===================================================================

    @Override
    public void evictPostListCache() {
        // 切换版本即可让列表/搜索缓存失效，旧版本按TTL回收，无需全库SCAN。
        AfterCommit.run(() -> {
            CacheVersion.invalidate(redisTemplate, KEY_LIST, "post:lists");
        });
    }

    @Override
    public void evictPostDetailByAuthor(Long authorId) {
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .select(Post::getId)
                .eq(Post::getAuthorId, authorId));
        evictDetailKeys(posts);
    }

    @Override
    public void evictPostDetailByCategory(Integer categoryId) {
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .select(Post::getId)
                .eq(Post::getCategoryId, categoryId));
        evictDetailKeys(posts);
    }

    private void evictDetailKeys(List<Post> posts) {
        if (posts.isEmpty()) return;
        for (Post p : posts) invalidateDetail(p.getId());
    }

    /** 直接查 DB（降级路径 + 兜底） */
    private Post queryPostDirectly(Long id) {
        Post post = postMapper.selectPostDetail(id);
        if (post == null || post.getStatus() != 1) return null;
        incrementViewCount(id);
        return post;
    }

    /** 浏览计数写入 Redis（异步定时刷到 MySQL） */
    private void incrementViewCount(Long postId) {
        try {
            redisTemplate.opsForValue().increment(KEY_VIEW_COUNT + postId, 1);
        } catch (Exception ignored) {
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
