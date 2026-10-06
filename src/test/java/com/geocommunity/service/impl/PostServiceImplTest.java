package com.geocommunity.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import com.geocommunity.dto.PostVO;
import com.geocommunity.entity.Category;
import com.geocommunity.entity.Comment;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.PostFavorite;
import com.geocommunity.entity.PostLike;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.CategoryMapper;
import com.geocommunity.mapper.PostFavoriteMapper;
import com.geocommunity.mapper.PostLikeMapper;
import com.geocommunity.mapper.PostMapper;
import com.geocommunity.mapper.UserMapper;
import com.geocommunity.common.exception.BusinessException;
import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.mapper.CommentMapper;
import com.geocommunity.mq.MqEvent;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostMapper postMapper;
    @Mock
    private PostLikeMapper postLikeMapper;
    @Mock
    private PostFavoriteMapper postFavoriteMapper;
    @Mock
    private com.geocommunity.mq.OutboxService outboxService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private UserMapper userMapper;

    @Mock
    private CommentMapper commentMapper;
    @Mock
    private JsonUtil jsonUtil;
    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private PostServiceImpl postService;

    private Post mockPost;
    private final Long postId = 1L;
    private final Long userId = 100L;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        // Lambda 包装器需要实体 TableInfo 元数据缓存，纯 Mockito 环境手动补齐
        for (Class<?> clazz : new Class<?>[]{Post.class, PostLike.class, PostFavorite.class, Comment.class,
                User.class, Category.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
        }

        mockPost = new Post();
        mockPost.setId(postId);
        mockPost.setTitle("周末爬山");
        mockPost.setContent("有人一起吗？");
        mockPost.setAuthorId(userId);
        mockPost.setCategoryId(1);
        mockPost.setLikeCount(10);
        mockPost.setCommentCount(3);
        mockPost.setViewCount(200);
        mockPost.setStatus(1);
        mockPost.setCreatedAt(LocalDateTime.now());
        User author = new User(); author.setId(userId); author.setStatus(1);
        lenient().when(userMapper.selectForUpdate(userId)).thenReturn(author);
    }

    @Test
    void createPost_shouldSetCorrectFields() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(categoryMapper.selectForUpdate(1)).thenReturn(new Category());

        Post input = new Post();
        input.setTitle("测试帖");
        input.setContent("测试内容");
        input.setCategoryId(1);

        Post created = postService.createPost(input, userId);

        assertNotNull(created.getAuthorId());
        assertEquals(userId, created.getAuthorId());
        assertEquals(1, created.getStatus().intValue());
        assertNotNull(created.getCreatedAt());
        // 通知仍然走 MQ
        verify(outboxService).enqueue(any(MqEvent.class));
    }

    @Test
    void likePost_shouldSucceed_whenFirstTime() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        postService.likePost(postId, userId);

        verify(postLikeMapper).insert(any(PostLike.class));
        verify(postMapper).update(any(), any());
        // 热榜改为 Redis ZSET，不走 MQ
        verify(zSetOperations).incrementScore("post:hot:24h", String.valueOf(postId), 2);
    }

    @Test
    void likePost_shouldThrow_whenDuplicate() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(postLikeMapper.insert(any(PostLike.class))).thenThrow(new DuplicateKeyException(""));

        assertThrows(BusinessException.class, () -> postService.likePost(postId, userId));
    }

    @Test
    void likePost_shouldThrow_whenPostNotExist() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(null);

        assertThrows(BusinessException.class, () -> postService.likePost(postId, userId));
    }

    @Test
    void unlikePost_shouldSucceed() {
        PostLike existing = new PostLike();
        existing.setId(1L);
        existing.setPostId(postId);
        existing.setUserId(userId);

        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(postLikeMapper.delete(any())).thenReturn(1);

        postService.unlikePost(postId, userId);

        verify(postLikeMapper).delete(any());
        // 热榜改为 Redis ZSET
        verify(zSetOperations).incrementScore("post:hot:24h", String.valueOf(postId), -2);
    }

    // ==================== 收藏 / 取消收藏 ====================

    @Test
    void favoritePost_shouldSucceed() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        postService.favoritePost(postId, userId);

        verify(postFavoriteMapper).insert(any(PostFavorite.class));
        verify(redisTemplate.opsForZSet()).incrementScore("post:hot:24h", String.valueOf(postId), 4);
    }

    @Test
    void favoritePost_shouldThrow_whenDuplicate() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(postFavoriteMapper.insert(any(PostFavorite.class))).thenThrow(new DuplicateKeyException(""));

        assertThrows(BusinessException.class, () -> postService.favoritePost(postId, userId));
    }

    @Test
    void unfavoritePost_shouldSucceed() {
        PostFavorite existing = new PostFavorite();
        existing.setId(1L);
        existing.setPostId(postId);
        existing.setUserId(userId);

        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(postFavoriteMapper.delete(any())).thenReturn(1);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        postService.unfavoritePost(postId, userId);

        verify(postFavoriteMapper).delete(any());
        verify(redisTemplate.opsForZSet()).incrementScore("post:hot:24h", String.valueOf(postId), -4);
    }

    @Test
    void unfavoritePost_shouldThrow_whenNotFavorited() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(postFavoriteMapper.delete(any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> postService.unfavoritePost(postId, userId));
    }

    // ==================== 更新帖子 ====================

    @Test
    void updatePost_shouldSucceed() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        Post update = new Post();
        update.setTitle("新标题");
        update.setContent("新内容");

        postService.updatePost(postId, update, userId);

        verify(postMapper).updateById(any(Post.class));
        verify(redisTemplate).delete("post:detail:" + postId);
    }

    @Test
    void updatePost_shouldThrow_whenNotOwner() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);

        Post update = new Post();
        update.setTitle("新标题");

        assertThrows(BusinessException.class,
                () -> postService.updatePost(postId, update, 999L));
    }

    @Test
    void updatePost_shouldThrow_whenPostNotExist() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> postService.updatePost(postId, new Post(), userId));
    }

    // ==================== 删除帖子 ====================

    @Test
    void deletePost_shouldSucceed() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        when(postMapper.deleteById(postId)).thenReturn(1);
        postService.deletePost(postId, userId);

        verify(postMapper).deleteById(postId);
        verify(postLikeMapper).delete(any());
        verify(postFavoriteMapper).delete(any());
        verify(commentMapper).delete(any());
        verify(zSetOperations).remove("post:hot:24h", String.valueOf(postId));
    }

    @Test
    void deletePost_shouldThrow_whenNotOwner() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);

        assertThrows(BusinessException.class,
                () -> postService.deletePost(postId, 999L));
    }

    @Test
    void deletePost_shouldThrow_whenPostNotExist() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> postService.deletePost(postId, userId));
    }

    // ==================== 帖子详情（带缓存三灾） ====================

    @Test
    void getDetail_shouldReturnNull_whenNullMarkerExists() {
        when(redisTemplate.hasKey("post:null:" + postId)).thenReturn(true);

        assertNull(postService.getDetail(postId));
        verify(postMapper, never()).selectPostDetail(any());
    }

    @Test
    void getDetail_shouldReturnCachedPost() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("post:null:" + postId)).thenReturn(false);
        when(redisTemplate.opsForValue().get("post:detail:" + postId)).thenReturn("cached_json");
        when(jsonUtil.fromJson("cached_json", Post.class)).thenReturn(mockPost);

        Post result = postService.getDetail(postId);
        assertNotNull(result);
        assertEquals("周末爬山", result.getTitle());
        // 浏览计数递增
        verify(redisTemplate.opsForValue()).increment("post:view:" + postId, 1);
    }

    @Test
    void getDetail_shouldQueryDbAndCache_whenCacheMiss() throws Exception {
        RLock mockLock = mock(RLock.class);
        when(mockLock.tryLock(0, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("post:null:" + postId)).thenReturn(false);
        when(redisTemplate.opsForValue().get("post:detail:" + postId)).thenReturn(null);
        when(redissonClient.getLock("lock:detail:" + postId)).thenReturn(mockLock);
        when(postMapper.selectPostDetail(postId)).thenReturn(mockPost);
        when(jsonUtil.toJson(mockPost)).thenReturn("post_json");

        Post result = postService.getDetail(postId);
        assertNotNull(result);
        assertEquals("周末爬山", result.getTitle());
        verify(redisTemplate.opsForValue()).set(eq("post:detail:" + postId), eq("post_json"), anyLong(), eq(TimeUnit.SECONDS));
        verify(mockLock).unlock();
    }

    @Test
    void getDetail_shouldCacheNullMarker_whenPostNotExist() throws Exception {
        RLock mockLock = mock(RLock.class);
        when(mockLock.tryLock(0, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("post:null:" + postId)).thenReturn(false);
        when(redisTemplate.opsForValue().get("post:detail:" + postId)).thenReturn(null);
        when(redissonClient.getLock("lock:detail:" + postId)).thenReturn(mockLock);
        when(postMapper.selectPostDetail(postId)).thenReturn(null);

        assertNull(postService.getDetail(postId));
        verify(redisTemplate.opsForValue()).set("post:null:" + postId, "1", 5, TimeUnit.MINUTES);
        verify(mockLock).unlock();
    }

    @Test
    void getDetail_shouldReturnNull_whenPostDeleted() throws Exception {
        RLock mockLock = mock(RLock.class);
        when(mockLock.tryLock(0, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("post:null:" + postId)).thenReturn(false);
        when(redisTemplate.opsForValue().get("post:detail:" + postId)).thenReturn(null);
        when(redissonClient.getLock("lock:detail:" + postId)).thenReturn(mockLock);
        mockPost.setStatus(0);
        when(postMapper.selectPostDetail(postId)).thenReturn(mockPost);

        assertNull(postService.getDetail(postId));
        verify(redisTemplate.opsForValue()).set("post:null:" + postId, "1", 5, TimeUnit.MINUTES);
    }

    @Test
    void likePost_shouldUpdateHotBoardScore2() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        postService.likePost(postId, userId);

        verify(zSetOperations).incrementScore("post:hot:24h", String.valueOf(postId), 2);
    }

    @Test
    void unlikePost_shouldUpdateHotBoardScoreMinus2() {
        PostLike existing = new PostLike();
        existing.setId(1L);
        existing.setPostId(postId);
        existing.setUserId(userId);

        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(postLikeMapper.delete(any())).thenReturn(1);

        postService.unlikePost(postId, userId);

        verify(zSetOperations).incrementScore("post:hot:24h", String.valueOf(postId), -2);
    }
}
