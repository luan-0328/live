package com.geocommunity.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.geocommunity.common.auth.SessionService;
import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.common.utils.AfterCommit;
import com.geocommunity.common.utils.CacheVersion;
import com.geocommunity.mq.OutboxService;
import com.geocommunity.common.utils.PasswordUtil;
import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.exception.BusinessException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import com.geocommunity.dto.PostVO;
import com.geocommunity.dto.UpdatePasswordRequest;
import com.geocommunity.dto.UpdatePhoneRequest;
import com.geocommunity.dto.UpdateProfileRequest;
import com.geocommunity.entity.Comment;
import com.geocommunity.entity.Notification;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.PostFavorite;
import com.geocommunity.entity.PostLike;
import com.geocommunity.entity.Report;
import com.geocommunity.entity.User;
import com.geocommunity.entity.UserFollow;
import com.geocommunity.mapper.CommentMapper;
import com.geocommunity.mapper.NotificationMapper;
import com.geocommunity.mapper.PostFavoriteMapper;
import com.geocommunity.mapper.PostLikeMapper;
import com.geocommunity.mapper.PostMapper;
import com.geocommunity.mapper.ReportMapper;
import com.geocommunity.mapper.UserFollowMapper;
import com.geocommunity.mapper.UserMapper;
import com.geocommunity.mq.MqEvent;
import com.geocommunity.service.PostService;
import com.geocommunity.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserFollowMapper userFollowMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private PostFavoriteMapper postFavoriteMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private PostService postService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private JsonUtil jsonUtil;

    // ===================================================================
    //  缓存 Key 常量（统一在 RedisKeys 中管理）
    // ===================================================================
    private static final String KEY_USER = RedisKeys.USER_CACHE;
    private static final String KEY_USER_NULL = RedisKeys.USER_NULL;
    private static final String LOCK_USER = RedisKeys.LOCK_USER;

    // ===================================================================
    //  用户信息缓存（Cache-Aside 手动缓存 + 三灾防护）
    // ===================================================================
    //
    //  穿透防护：DB 查不到时缓存空值标记（userInfo:null:{id}，TTL 5min）
    //  击穿防护：分布式锁（lock:user:{id}），抢到锁的线程查 DB 写缓存，其他等或回源
    //  雪崩防护：TTL 30min + 随机 0~10min
    //
    //  双写一致性：更新时先改 DB，再删缓存
    // ===================================================================

    @Override
    public User getById(Serializable id) {
        if (id == null) return null;
        String cacheKey = CacheVersion.key(redisTemplate, KEY_USER + id, KEY_USER + id);
        String nullKey = cacheKey.equals(KEY_USER + id) ? KEY_USER_NULL + id : cacheKey + ":null";

        // 1. 查空值标记（穿透保护）
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nullKey))) {
            return null;
        }

        // 2. 读缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return jsonUtil.fromJson(cached, User.class);
        }

        // 3. 分布式锁 —— 防止缓存击穿
        String lockKey = LOCK_USER + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(0, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            locked = false;
        }

        if (!locked) {
            // 没抢到锁：休眠 50ms 后尝试从缓存拿
            sleep(50);
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return jsonUtil.fromJson(cached, User.class);
            }
            // 等了还没缓存 → 直接查 DB（降级）
            return queryUserDirectly(id);
        }

        try {
            // 4. 双重检查
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return jsonUtil.fromJson(cached, User.class);
            }

            // 5. 查 DB
            User user = baseMapper.selectById(id);
            if (user == null || user.getStatus() != 1) {
                // 穿透防护：缓存空值标记
                redisTemplate.opsForValue().set(nullKey, "1", 5, TimeUnit.MINUTES);
                return null;
            }

            // 6. 写缓存（TTL = 30min + 随机 0~10min 防雪崩）
            //    缓存与响应都不带密码哈希
            user.setPassword(null);
            int ttl = 1800 + ThreadLocalRandom.current().nextInt(600);
            redisTemplate.opsForValue().set(cacheKey, jsonUtil.toJson(user), ttl, TimeUnit.SECONDS);

            return user;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private User queryUserDirectly(Serializable id) {
        User user = baseMapper.selectById(id);
        if (user == null || user.getStatus() != 1) return null;
        user.setPassword(null);
        return user;
    }

    // ==================== 已有方法 ====================

    @Override
    public User findByPhone(String phone) {
        // 排除已注销账号（status=-1）；封禁用户（status=0）要能被查到，登录时返回「账号已封禁」
        return lambdaQuery().eq(User::getPhone, phone).ne(User::getStatus, -1).one();
    }

    @Override
    public boolean isPhoneExists(String phone) {
        return lambdaQuery().eq(User::getPhone, phone).count() > 0;
    }

    @Override
    public User findAdmin(String account, String password) {
        User admin = lambdaQuery()
                .eq(User::getPhone, account)
                .eq(User::getRole, "ROLE_ADMIN")
                .eq(User::getStatus, 1)
                .one();
        if (admin == null || !PasswordUtil.matches(password, admin.getPassword())) {
            return null;
        }
        return admin;
    }

    // ==================== 用户信息 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UpdateProfileRequest req) {
        User user = new User();
        user.setId(userId);
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }
        baseMapper.updateById(user);

        // 先改 DB ↑，再删缓存 ↓
        invalidateUser(userId);
        postService.evictPostDetailByAuthor(userId);
        postService.evictPostListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePhone(Long userId, UpdatePhoneRequest req) {
        String code = redisTemplate.opsForValue().get(RedisKeys.SMS_CODE + req.getNewPhone());
        if (code == null || !code.equals(req.getCode())) {
            throw new BusinessException("验证码错误或过期");
        }
        redisTemplate.delete(RedisKeys.SMS_CODE + req.getNewPhone());

        User user = new User();
        user.setId(userId);
        user.setPhone(req.getNewPhone());
        try {
            baseMapper.updateById(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(1009, "手机号已注册");
        }

        // 先改 DB ↑，再删缓存 ↓
        invalidateUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(Long userId, UpdatePasswordRequest req) {
        User user = baseMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (StrUtil.isBlank(user.getPassword())) {
            throw new BusinessException(400, "未设置密码，请通过其他方式设置");
        }
        if (!PasswordUtil.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }
        user.setPassword(PasswordUtil.hash(req.getNewPassword()));
        baseMapper.updateById(user);

        // 先改 DB ↑，再删缓存 ↓
        invalidateUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId) {
        User user = baseMapper.selectForUpdate(userId);
        if (user == null || Integer.valueOf(-1).equals(user.getStatus())) throw new BusinessException(1006, "账号已注销");
        baseMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId).set(User::getStatus, -1));
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>().eq(Post::getAuthorId, userId));
        for (Post post : posts) postService.removePost(post.getId());
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getAuthorId, userId));
        for (Comment c : comments) {
            Post post = postMapper.selectActiveForUpdate(c.getPostId());
            if (commentMapper.deleteById(c.getId()) == 1 && post != null) {
                postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, c.getPostId())
                        .setSql("comment_count = GREATEST(CAST(comment_count AS SIGNED) - 1, 0)"));
                invalidatePost(c.getPostId(), -3);
            }
        }
        List<PostLike> likes = postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>().eq(PostLike::getUserId, userId));
        for (PostLike like : likes) {
            Post post = postMapper.selectActiveForUpdate(like.getPostId());
            if (postLikeMapper.deleteById(like.getId()) == 1 && post != null) {
                postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, like.getPostId())
                        .setSql("like_count = GREATEST(CAST(like_count AS SIGNED) - 1, 0)"));
                invalidatePost(like.getPostId(), -2);
            }
        }
        List<PostFavorite> favorites = postFavoriteMapper.selectList(new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getUserId, userId));
        for (PostFavorite f : favorites) {
            if (postFavoriteMapper.deleteById(f.getId()) == 1) invalidatePost(f.getPostId(), -4);
        }
        List<UserFollow> following = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, userId));
        for (UserFollow f : following) {
            if (userFollowMapper.deleteById(f.getId()) == 1) {
                baseMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, f.getFolloweeId())
                        .setSql("follower_count = GREATEST(CAST(follower_count AS SIGNED) - 1, 0)"));
                invalidateUser(f.getFolloweeId());
            }
        }
        List<UserFollow> followers = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFolloweeId, userId));
        for (UserFollow f : followers) {
            if (userFollowMapper.deleteById(f.getId()) == 1) {
                baseMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, f.getFollowerId())
                        .setSql("following_count = GREATEST(CAST(following_count AS SIGNED) - 1, 0)"));
                invalidateUser(f.getFollowerId());
            }
        }
        baseMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, userId)
                .setSql("post_count = 0, follower_count = 0, following_count = 0"));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>().eq(Notification::getUserId, userId));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>().eq(Notification::getFromUserId, userId));
        reportMapper.delete(new LambdaQueryWrapper<Report>().eq(Report::getReporterId, userId));
        invalidateUser(userId); postService.evictPostListCache();
        AfterCommit.run(() -> sessionService.forceLogout(userId));
    }

    // ==================== 关注 / 取消关注 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followUser(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(1007, "不能关注自己");
        }

        // 在插入关系前按固定顺序锁定双方，避免与账号注销竞争产生悬空关注。
        User first = baseMapper.selectForUpdate(Math.min(currentUserId, targetUserId));
        User second = baseMapper.selectForUpdate(Math.max(currentUserId, targetUserId));
        User current = currentUserId < targetUserId ? first : second;
        User target = currentUserId < targetUserId ? second : first;
        if (current == null || !Integer.valueOf(1).equals(current.getStatus())) throw new BusinessException(401, "账号不可用");
        if (target == null || target.getStatus() != 1) {
            throw new BusinessException(1007, "用户不存在");
        }

        UserFollow follow = new UserFollow();
        follow.setFollowerId(currentUserId);
        follow.setFolloweeId(targetUserId);
        try {
            userFollowMapper.insert(follow);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(1007, "已关注该用户");
        }

        adjustFollowCounts(currentUserId, targetUserId, true);
        invalidateUser(currentUserId); invalidateUser(targetUserId);
        outboxService.enqueue(new MqEvent("FOLLOW", null, currentUserId, targetUserId, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollowUser(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(1007, "不能取消关注自己");
        }

        int deleted = userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId).eq(UserFollow::getFolloweeId, targetUserId));
        if (deleted != 1) throw new BusinessException(1007, "未关注该用户");
        adjustFollowCounts(currentUserId, targetUserId, false);
        invalidateUser(currentUserId); invalidateUser(targetUserId);
    }

    @Override
    public Page<User> getFollowers(Long userId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        Page<UserFollow> followPage = userFollowMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFolloweeId, userId)
                        .orderByDesc(UserFollow::getCreatedAt).orderByDesc(UserFollow::getId));

        List<Long> userIds = followPage.getRecords().stream()
                .map(UserFollow::getFollowerId)
                .collect(Collectors.toList());

        List<User> users = userIds.isEmpty() ? List.of() : baseMapper.selectBatchIds(userIds);
        java.util.Map<Long,User> byId=users.stream().collect(Collectors.toMap(User::getId,u->u));
        users=userIds.stream().map(byId::get).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        users.forEach(u -> { u.setPassword(null); u.setPhone(null); });

        Page<User> result = new Page<>(followPage.getCurrent(), followPage.getSize(), followPage.getTotal());
        result.setRecords(users);
        return result;
    }

    @Override
    public Page<User> getFollowing(Long userId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        Page<UserFollow> followPage = userFollowMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .orderByDesc(UserFollow::getCreatedAt).orderByDesc(UserFollow::getId));

        List<Long> userIds = followPage.getRecords().stream()
                .map(UserFollow::getFolloweeId)
                .collect(Collectors.toList());

        List<User> users = userIds.isEmpty() ? List.of() : baseMapper.selectBatchIds(userIds);
        java.util.Map<Long,User> byId=users.stream().collect(Collectors.toMap(User::getId,u->u));
        users=userIds.stream().map(byId::get).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        users.forEach(u -> { u.setPassword(null); u.setPhone(null); });

        Page<User> result = new Page<>(followPage.getCurrent(), followPage.getSize(), followPage.getTotal());
        result.setRecords(users);
        return result;
    }

    // ==================== 关注关系 ====================

    @Override
    public boolean isFollowing(Long currentUserId, Long targetUserId) {
        return userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFolloweeId, targetUserId)) > 0;
    }

    // ==================== 收藏列表 ====================

    @Override
    public Page<PostVO> getFavorites(Long userId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        Page<PostVO> pageParam = new Page<>(page, size);
        pageParam.setOptimizeCountSql(false);
        return postMapper.selectFavoritePosts(pageParam, userId);
    }

    private void adjustFollowCounts(Long follower, Long followee, boolean add) {
        // 固定用户行的更新顺序，降低相互关注时的死锁概率。
        Long first = Math.min(follower, followee), second = Math.max(follower, followee);
        for (Long id : List.of(first, second)) {
            String column = id.equals(follower) ? "following_count" : "follower_count";
            String sql = add ? column + " = " + column + " + 1"
                    : column + " = GREATEST(CAST(" + column + " AS SIGNED) - 1, 0)";
            baseMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id).setSql(sql));
        }
    }

    private void invalidateUser(Long id) {
        AfterCommit.run(() -> {
            CacheVersion.invalidate(redisTemplate, KEY_USER + id, KEY_USER + id);
            redisTemplate.delete(KEY_USER_NULL + id);
        });
    }

    private void invalidatePost(Long id, int scoreDelta) {
        AfterCommit.run(() -> {
            CacheVersion.invalidate(redisTemplate, RedisKeys.POST_DETAIL + id, RedisKeys.POST_DETAIL + id);
            redisTemplate.opsForZSet().incrementScore(RedisKeys.POST_HOT_BOARD, String.valueOf(id), scoreDelta);
        });
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
