package com.geocommunity.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.common.auth.SessionService;
import com.geocommunity.common.exception.BusinessException;
import com.geocommunity.dto.PostVO;
import com.geocommunity.dto.UpdatePasswordRequest;
import com.geocommunity.dto.UpdateProfileRequest;
import com.geocommunity.entity.Category;
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
import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.common.utils.PasswordUtil;
import com.geocommunity.service.PostService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserFollowMapper userFollowMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private PostLikeMapper postLikeMapper;
    @Mock
    private PostFavoriteMapper postFavoriteMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private ReportMapper reportMapper;
    @Mock
    private PostService postService;
    @Mock
    private SessionService sessionService;
    @Mock
    private com.geocommunity.mq.OutboxService outboxService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private JsonUtil jsonUtil;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UserServiceImpl userService;

    private User mockUser;
    private final Long userId = 100L;
    private final Long targetUserId = 200L;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        // ServiceImpl<M,T> 的 baseMapper 需要手动注入（Mockito 不处理继承的泛型字段）
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);

        // LambdaUpdateWrapper/LambdaQueryWrapper 需要实体 TableInfo 元数据缓存；
        // 纯 Mockito 环境不会自动初始化，这里手动补齐，避免用例执行顺序影响结果
        for (Class<?> clazz : new Class<?>[]{User.class, UserFollow.class, Post.class, Comment.class,
                PostLike.class, PostFavorite.class, Notification.class, Report.class, Category.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), clazz);
        }

        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setPhone("13800138000");
        mockUser.setNickname("测试用户");
        mockUser.setAvatar("http://example.com/avatar.png");
        mockUser.setPassword(PasswordUtil.hash("oldPass123"));
        mockUser.setRole("ROLE_USER");
        mockUser.setStatus(1);
        mockUser.setFollowerCount(10);
        mockUser.setFollowingCount(5);
        mockUser.setPostCount(3);
        mockUser.setCreatedAt(LocalDateTime.now());
        lenient().when(userMapper.selectForUpdate(userId)).thenReturn(mockUser);
    }

    // ==================== 用户信息缓存（getById） ====================

    @Test
    void getById_shouldReturnNull_whenNullMarkerExists() {
        when(redisTemplate.hasKey("user:null:" + userId)).thenReturn(true);

        assertNull(userService.getById(userId));
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void getById_shouldReturnCachedUser() {
        when(redisTemplate.hasKey("user:null:" + userId)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForValue().get("user:" + userId)).thenReturn("cached_json");
        when(jsonUtil.fromJson("cached_json", User.class)).thenReturn(mockUser);

        User result = userService.getById(userId);
        assertNotNull(result);
        assertEquals("测试用户", result.getNickname());
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void getById_shouldQueryDbAndCache_whenCacheMiss() throws Exception {
        RLock mockLock = mock(RLock.class);
        when(mockLock.tryLock(0, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        when(redisTemplate.hasKey("user:null:" + userId)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForValue().get("user:" + userId)).thenReturn(null);
        when(redissonClient.getLock("lock:user:" + userId)).thenReturn(mockLock);
        when(userMapper.selectById(userId)).thenReturn(mockUser);
        when(jsonUtil.toJson(mockUser)).thenReturn("user_json");

        User result = userService.getById(userId);
        assertNotNull(result);
        assertEquals("测试用户", result.getNickname());
        verify(redisTemplate.opsForValue()).set(eq("user:" + userId), eq("user_json"), anyLong(), eq(TimeUnit.SECONDS));
        verify(mockLock).unlock();
    }

    @Test
    void getById_shouldCacheNullMarker_whenUserNotExist() throws Exception {
        RLock mockLock = mock(RLock.class);
        when(mockLock.tryLock(0, 5, TimeUnit.SECONDS)).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);

        when(redisTemplate.hasKey("user:null:" + userId)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForValue().get("user:" + userId)).thenReturn(null);
        when(redissonClient.getLock("lock:user:" + userId)).thenReturn(mockLock);
        when(userMapper.selectById(userId)).thenReturn(null);

        assertNull(userService.getById(userId));
        verify(redisTemplate.opsForValue()).set("user:null:" + userId, "1", 5, TimeUnit.MINUTES);
        verify(mockLock).unlock();
    }

    // ==================== 更新资料 ====================

    @Test
    void updateProfile_shouldUpdateAndEvictCache() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setNickname("新昵称");
        req.setAvatar("http://new-avatar.png");
        userService.updateProfile(userId, req);

        verify(userMapper).updateById(any(User.class));
        verify(redisTemplate).delete("user:" + userId);
        verify(redisTemplate).delete("user:null:" + userId);
    }

    @Test
    void updatePassword_shouldSucceed_withOldPassword() {
        when(userMapper.selectById(userId)).thenReturn(mockUser);

        UpdatePasswordRequest req = new UpdatePasswordRequest();
        req.setOldPassword("oldPass123");
        req.setNewPassword("newPass456");
        userService.updatePassword(userId, req);

        verify(userMapper).updateById(any(User.class));
        verify(redisTemplate).delete("user:" + userId);
    }

    @Test
    void updatePassword_shouldThrow_whenOldPasswordWrong() {
        when(userMapper.selectById(userId)).thenReturn(mockUser);

        UpdatePasswordRequest req = new UpdatePasswordRequest();
        req.setOldPassword("wrongPass");
        req.setNewPassword("newPass456");

        assertThrows(BusinessException.class, () -> userService.updatePassword(userId, req));
    }

    @Test
    void updatePassword_shouldThrow_whenUserNotExist() {
        when(userMapper.selectById(userId)).thenReturn(null);

        UpdatePasswordRequest req = new UpdatePasswordRequest();
        req.setOldPassword("oldPass123");
        req.setNewPassword("newPass456");

        assertThrows(BusinessException.class, () -> userService.updatePassword(userId, req));
    }

    @Test
    void deleteAccount_shouldSetStatusMinus1AndForceLogout() {
        when(userMapper.selectForUpdate(userId)).thenReturn(mockUser);
        when(postMapper.selectList(any())).thenReturn(List.of());
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(postLikeMapper.selectList(any())).thenReturn(List.of());
        when(postFavoriteMapper.selectList(any())).thenReturn(List.of());
        when(userFollowMapper.selectList(any())).thenReturn(List.of());
        userService.deleteAccount(userId);
        verify(userMapper, times(2)).update(any(), any());
        verify(redisTemplate).delete("user:" + userId);
        verify(redisTemplate).delete("user:null:" + userId);
        verify(postService).evictPostListCache();
        verify(sessionService).forceLogout(userId);
    }

    // ==================== 关注系统 ====================

    @Test
    void followUser_shouldSucceed() {
        when(userMapper.selectForUpdate(targetUserId)).thenReturn(mockUser);

        userService.followUser(userId, targetUserId);

        verify(userFollowMapper).insert(any(UserFollow.class));
        verify(userMapper, times(2)).update(any(), any());
    }

    @Test
    void followUser_shouldThrow_whenFollowSelf() {
        assertThrows(BusinessException.class, () -> userService.followUser(userId, userId));
        verify(userFollowMapper, never()).insert(any(UserFollow.class));
    }

    @Test
    void followUser_shouldThrow_whenTargetNotExist() {
        when(userMapper.selectForUpdate(targetUserId)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.followUser(userId, targetUserId));
    }

    @Test
    void followUser_shouldThrow_whenDuplicate() {
        when(userMapper.selectForUpdate(targetUserId)).thenReturn(mockUser);
        when(userFollowMapper.insert(any(UserFollow.class))).thenThrow(new DuplicateKeyException(""));

        assertThrows(BusinessException.class, () -> userService.followUser(userId, targetUserId));
    }

    @Test
    void unfollowUser_shouldSucceed() {
        UserFollow existing = new UserFollow();
        existing.setId(1L);
        existing.setFollowerId(userId);
        existing.setFolloweeId(targetUserId);

        when(userFollowMapper.delete(any())).thenReturn(1);

        userService.unfollowUser(userId, targetUserId);

        verify(userFollowMapper).delete(any());
        verify(userMapper, times(2)).update(any(), any());
    }

    @Test
    void unfollowUser_shouldThrow_whenNotFollowing() {
        when(userFollowMapper.delete(any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> userService.unfollowUser(userId, targetUserId));
    }

    @Test
    void getFollowers_shouldReturnPaginatedUsers() {
        UserFollow follow = new UserFollow();
        follow.setFollowerId(userId);
        follow.setFolloweeId(targetUserId);
        Page<UserFollow> followPage = new Page<>(1, 20);
        followPage.setRecords(List.of(follow));
        followPage.setTotal(1);

        when(userFollowMapper.selectPage(any(), any())).thenReturn(followPage);
        when(userMapper.selectBatchIds(List.of(userId))).thenReturn(List.of(mockUser));

        Page<User> result = userService.getFollowers(targetUserId, 1, 20);
        assertEquals(1, result.getRecords().size());
        assertEquals(userId, result.getRecords().get(0).getId());
    }

    @Test
    void getFollowing_shouldReturnPaginatedUsers() {
        mockUser.setId(targetUserId);
        UserFollow follow = new UserFollow();
        follow.setFollowerId(userId);
        follow.setFolloweeId(targetUserId);
        Page<UserFollow> followPage = new Page<>(1, 20);
        followPage.setRecords(List.of(follow));
        followPage.setTotal(1);

        when(userFollowMapper.selectPage(any(), any())).thenReturn(followPage);
        when(userMapper.selectBatchIds(List.of(targetUserId))).thenReturn(List.of(mockUser));

        Page<User> result = userService.getFollowing(userId, 1, 20);
        assertEquals(1, result.getRecords().size());
        assertEquals(targetUserId, result.getRecords().get(0).getId());
    }

    // ==================== 收藏列表 ====================

    @Test
    void getFavorites_shouldReturnPaginatedPosts() {
        Page<PostVO> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(new PostVO()));
        mockPage.setTotal(1);

        when(postMapper.selectFavoritePosts(any(), eq(userId))).thenReturn(mockPage);

        Page<PostVO> result = userService.getFavorites(userId, 1, 20);
        assertEquals(1, result.getRecords().size());
    }

    // ==================== 遗留方法（需要完整的 MyBatis-Plus 初始化，适合集成测试） ====================
    // findByPhone / isPhoneExists / findAdmin / updatePhone
    // 这些方法使用 ServiceImpl.lambdaQuery()，MyBatis-Plus 3.5.7 要求 mapper 是真实代理对象，
    // 纯 Mockito 无法模拟，建议通过 @SpringBootTest 做集成测试。
}
