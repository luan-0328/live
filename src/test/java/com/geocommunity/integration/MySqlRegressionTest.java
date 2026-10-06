package com.geocommunity.integration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.geocommunity.common.auth.SessionService;
import com.geocommunity.common.exception.BusinessException;
import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.config.MyBatisPlusConfig;
import com.geocommunity.entity.*;
import com.geocommunity.mapper.*;
import com.geocommunity.mq.*;
import com.geocommunity.service.PostService;
import com.geocommunity.service.UserService;
import com.geocommunity.service.impl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.*;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 显式提供独立MySQL测试地址时运行，每项测试创建并销毁自己的随机数据库。 */
@EnabledIfEnvironmentVariable(named = "GEO_TEST_MYSQL_URL", matches = ".+")
class MySqlRegressionTest {
    private String rootUrl, database;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private SqlSessionTemplate sql;
    private PostService posts;
    private UserService users;
    private OutboxService outbox;
    private StringRedisTemplate redis;
    private PostMapper postMapper;

    @BeforeEach void setup() throws Exception {
        rootUrl = System.getenv("GEO_TEST_MYSQL_URL");
        // 地址必须是无业务库名的本机专用服务，避免误用生产库。
        if (!rootUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:\\d+/\\?.*")) throw new IllegalArgumentException("只接受127.0.0.1独立端口、无库名的测试地址");
        database = "geo_audit_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = rootConnection(); Statement statement = c.createStatement()) {
            statement.execute("CREATE DATABASE " + database + " CHARACTER SET utf8mb4");
        }
        dataSource = new DriverManagerDataSource(rootUrl.replace("/?", "/" + database + "?"), "root", "");
        jdbc = new JdbcTemplate(dataSource);
        try (Connection c = dataSource.getConnection()) { ScriptUtils.executeSqlScript(c, new FileSystemResource("sql/schema.sql")); }
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new MyBatisPlusConfig().mybatisPlusInterceptor());
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource); factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/PostMapper.xml"), new ClassPathResource("mapper/NotificationMapper.xml"));
        var sessionFactory = factory.getObject();
        for (Class<?> mapper : List.of(UserMapper.class, CategoryMapper.class, CommentMapper.class, PostLikeMapper.class,
                PostFavoriteMapper.class, UserFollowMapper.class, OutboxMapper.class, ReportMapper.class, FailedNotificationMapper.class)) {
            sessionFactory.getConfiguration().addMapper(mapper);
        }
        sql = new SqlSessionTemplate(sessionFactory);
        postMapper = sql.getMapper(PostMapper.class);
        redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        when(redis.opsForZSet()).thenReturn(mock(ZSetOperations.class));
        when(redis.opsForSet()).thenReturn(mock(SetOperations.class));
        JsonUtil json = new JsonUtil(new ObjectMapper().findAndRegisterModules());
        outbox = new OutboxService(sql.getMapper(OutboxMapper.class), json);
        PostServiceImpl postService = new PostServiceImpl();
        for (String field : List.of("postMapper", "postLikeMapper", "postFavoriteMapper", "commentMapper", "userMapper", "categoryMapper")) {
            Class<?> type = PostServiceImpl.class.getDeclaredField(field).getType();
            ReflectionTestUtils.setField(postService, field, sql.getMapper(type));
        }
        ReflectionTestUtils.setField(postService, "redisTemplate", redis);
        ReflectionTestUtils.setField(postService, "outboxService", outbox);
        posts = transactional(postService, PostService.class);
        UserServiceImpl userService = new UserServiceImpl();
        ReflectionTestUtils.setField(userService, "baseMapper", sql.getMapper(UserMapper.class));
        ReflectionTestUtils.setField(userService, "userFollowMapper", sql.getMapper(UserFollowMapper.class));
        ReflectionTestUtils.setField(userService, "redisTemplate", redis);
        ReflectionTestUtils.setField(userService, "outboxService", outbox);
        users = transactional(userService, UserService.class);
        jdbc.update("INSERT INTO user(id,phone,nickname,post_count,follower_count,following_count) VALUES (1,'13800000001','author',1,0,1),(2,'13800000002','reader',0,1,0)");
        jdbc.update("INSERT INTO category(id,name) VALUES(1,'test')");
        jdbc.update("INSERT INTO post(id,title,content,category_id,author_id,like_count) VALUES(1,'original','body',1,1,2)");
    }
    private Connection rootConnection() throws SQLException { return DriverManager.getConnection(rootUrl, "root", ""); }
    private <T> T transactional(Object target, Class<T> type) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource), new AnnotationTransactionAttributeSource()));
        return type.cast(proxy.getProxy());
    }
    @AfterEach void cleanup() throws Exception {
        if (database != null) try (Connection c = rootConnection(); Statement statement = c.createStatement()) {
            statement.execute("DROP DATABASE " + database);
        }
    }
    private int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }
    private List<Boolean> concurrently(Runnable action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Callable<Boolean> call = () -> { ready.countDown(); start.await(); try { action.run(); return true; } catch (BusinessException e) { return false; } };
            var a = pool.submit(call); var b = pool.submit(call); assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentUnlikeOnlyDecrementsOnce() throws Exception {
        jdbc.update("INSERT INTO post_like(post_id,user_id) VALUES(1,2)");
        var outcomes = concurrently(() -> posts.unlikePost(1L, 2L));
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, count("SELECT like_count FROM post WHERE id=1"));
        assertEquals(0, count("SELECT COUNT(*) FROM post_like"));
    }
    @Test void concurrentUnfollowOnlyDecrementsEachUserOnce() throws Exception {
        jdbc.update("INSERT INTO user_follow(follower_id,followee_id) VALUES(1,2)");
        var outcomes = concurrently(() -> users.unfollowUser(1L, 2L));
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, count("SELECT following_count FROM user WHERE id=1"));
        assertEquals(0, count("SELECT follower_count FROM user WHERE id=2"));
    }
    @Test void editsCannotChangeProtectedColumnsInActualSql() {
        Post input = new Post(); input.setTitle("edited"); input.setAuthorId(2L); input.setLikeCount(999); input.setStatus(-1);
        posts.updatePost(1L, input, 1L);
        assertEquals(1, count("SELECT author_id FROM post WHERE id=1"));
        assertEquals(2, count("SELECT like_count FROM post WHERE id=1"));
        assertEquals(1, count("SELECT status FROM post WHERE id=1"));
        assertEquals("edited", jdbc.queryForObject("SELECT title FROM post WHERE id=1", String.class));
    }
    @Test void unifiedDeleteDecrementsOnceAndAdminCanSeeDeletedRow() {
        assertTrue(posts.removePost(1L)); assertFalse(posts.removePost(1L));
        assertEquals(0, count("SELECT post_count FROM user WHERE id=1"));
        assertNull(postMapper.selectById(1L));
        assertEquals(1, postMapper.selectAdminPosts(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1,20), null, null).getRecords().size());
    }
    @Test void notificationsDeduplicateByEventAndAllowDistinctCommentsAndNullPost() {
        var mapper = sql.getMapper(NotificationMapper.class);
        Notification first = notice("first", 1L), second = notice("second", 1L), follow = notice("follow", null);
        mapper.insertBatch(List.of(first, second)); mapper.insertBatch(List.of(first, second));
        mapper.insertBatch(List.of(follow)); mapper.insertBatch(List.of(follow));
        assertEquals(3, count("SELECT COUNT(*) FROM notification"));
    }
    private Notification notice(String event, Long postId) {
        Notification n = new Notification(); n.setEventId(event); n.setUserId(1L); n.setFromUserId(2L);
        n.setType(postId == null ? "follow" : "comment"); n.setContent("test"); n.setPostId(postId);
        n.setIsRead(false); n.setCreatedAt(java.time.LocalDateTime.now()); return n;
    }
    @Test void outboxRollsBackWithBusinessTransaction() {
        var tx = new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            outbox.enqueue(new MqEvent("LIKE", 1L, 2L, 1L, null));
            jdbc.update("UPDATE post SET like_count=like_count+1 WHERE id=1");
            throw new IllegalStateException("simulate rollback");
        }));
        assertEquals(0, count("SELECT COUNT(*) FROM notification_outbox"));
        assertEquals(2, count("SELECT like_count FROM post WHERE id=1"));
    }
    @Test void committedLikePersistsNotificationOutbox() {
        posts.likePost(1L, 2L);
        assertEquals(1, count("SELECT COUNT(*) FROM notification_outbox"));
        assertEquals(3, count("SELECT like_count FROM post WHERE id=1"));
    }
    @Test void creatingPostInitializesSystemFieldsAndCountsAuthor() {
        Post input = new Post(); input.setTitle("new"); input.setContent("body"); input.setCategoryId(1);
        input.setAuthorId(2L); input.setLikeCount(999); input.setViewCount(999);
        Post created = posts.createPost(input, 1L);
        assertEquals(1L, created.getAuthorId()); assertEquals(0, created.getLikeCount()); assertEquals(0, created.getViewCount());
        assertEquals(2, count("SELECT post_count FROM user WHERE id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM notification_outbox"));
    }

    @Test void migrationPreservesOldNotificationsAndAddsOutbox() throws Exception {
        jdbc.execute("ALTER TABLE notification DROP INDEX uk_user_event, DROP COLUMN event_id, ADD UNIQUE INDEX uk_user_from_type_post(user_id,from_user_id,type,post_id)");
        jdbc.execute("DROP TABLE notification_outbox");
        jdbc.update("INSERT INTO notification(user_id,from_user_id,type,content,post_id) VALUES(1,2,'follow','old',NULL),(1,2,'follow','old duplicate',NULL)");
        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c, new FileSystemResource("sql/migrations_2026-10-03.sql"));
        }
        assertEquals(2, count("SELECT COUNT(*) FROM notification"));
        assertEquals(2, count("SELECT COUNT(DISTINCT event_id) FROM notification"));
        assertEquals(0, count("SELECT COUNT(*) FROM notification_outbox"));
    }

    @Test void negativePageSizeStillReturnsOnlyOneRecord() {
        jdbc.update("INSERT INTO post(id,title,category_id,author_id) VALUES(2,'second',1,1)");
        var page = posts.listPosts(null, "latest", 1, -1, 1L);
        assertEquals(1, page.getSize()); assertEquals(1, page.getRecords().size()); assertEquals(2, page.getTotal());
    }

    @Test void nearbyQueryCrossesDateLineAndHandlesPolarCoordinates() {
        jdbc.update("UPDATE post SET longitude=179.99,latitude=0 WHERE id=1");
        assertEquals(1, posts.nearbyPosts(-179.99, 0.0, 10, null, 1, 20).getRecords().size());
        jdbc.update("UPDATE post SET longitude=120,latitude=90 WHERE id=1");
        assertEquals(1, posts.nearbyPosts(-10.0, 90.0, 10, null, 1, 20).getRecords().size());
    }

    @Test void concurrentReportHandlingDeletesAndDecrementsOnce() throws Exception {
        jdbc.update("INSERT INTO report(id,reporter_id,target_type,target_id,reason) VALUES(1,2,'post',1,'test')");
        ReportServiceImpl target = new ReportServiceImpl();
        ReflectionTestUtils.setField(target, "reportMapper", sql.getMapper(ReportMapper.class));
        ReflectionTestUtils.setField(target, "postService", posts);
        var reports = transactional(target, com.geocommunity.service.ReportService.class);
        var outcomes = concurrently(() -> reports.handleReport(1L, 1, "handled", 1L));
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, count("SELECT post_count FROM user WHERE id=1"));
        assertEquals(1, count("SELECT status FROM report WHERE id=1"));
    }

    @Test void concurrentCommentDeleteOnlyDecrementsOnce() throws Exception {
        jdbc.update("UPDATE post SET comment_count=1 WHERE id=1");
        jdbc.update("INSERT INTO comment(id,post_id,author_id,content) VALUES(10,1,2,'test')");
        CommentServiceImpl target = new CommentServiceImpl();
        ReflectionTestUtils.setField(target, "commentMapper", sql.getMapper(CommentMapper.class));
        ReflectionTestUtils.setField(target, "postMapper", postMapper);
        ReflectionTestUtils.setField(target, "redisTemplate", redis);
        var comments = transactional(target, com.geocommunity.service.CommentService.class);
        var outcomes = concurrently(() -> comments.deleteComment(10L, 2L));
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, count("SELECT comment_count FROM post WHERE id=1"));
        assertEquals(-1, count("SELECT status FROM comment WHERE id=10"));
    }

    @Test void repliesStayInTwoLevelsAndSurviveRootDeletionAsPlaceholder() {
        CommentServiceImpl target=new CommentServiceImpl();
        ReflectionTestUtils.setField(target,"commentMapper",sql.getMapper(CommentMapper.class));
        ReflectionTestUtils.setField(target,"postMapper",postMapper);
        ReflectionTestUtils.setField(target,"userMapper",sql.getMapper(UserMapper.class));
        ReflectionTestUtils.setField(target,"redisTemplate",redis);
        ReflectionTestUtils.setField(target,"outboxService",outbox);
        var comments=transactional(target,com.geocommunity.service.CommentService.class);
        Comment root=new Comment(); root.setContent("root"); root=comments.addComment(1L,root,1L);
        Comment child=new Comment(); child.setContent("child"); child.setParentId(root.getId());
        child=comments.addComment(1L,child,2L);
        Comment reply=new Comment(); reply.setContent("reply to child"); reply.setReplyToCommentId(child.getId());
        reply=comments.addComment(1L,reply,1L);
        assertEquals(root.getId(),reply.getParentId()); assertEquals(2L,reply.getReplyToUserId());
        var page=comments.listComments(1L,1,20);
        assertEquals(2,page.getRecords().get(0).getChildren().size());
        assertEquals("reader",page.getRecords().get(0).getChildren().get(1).getReplyToAuthor().getNickname());
        comments.deleteComment(root.getId(),1L);
        page=comments.listComments(1L,1,20);
        assertEquals(1,page.getTotal()); assertEquals("该评论已删除",page.getRecords().get(0).getContent());
        assertNull(page.getRecords().get(0).getAuthor()); assertEquals(2,page.getRecords().get(0).getChildren().size());
        assertEquals(2,count("SELECT comment_count FROM post WHERE id=1"));
    }
    @Test void deadLettersDeduplicateKeepRetryBudgetAndResolvePermanently() {
        var mapper=sql.getMapper(FailedNotificationMapper.class);
        var service=new FailedNotificationService(mapper,new JsonUtil(new ObjectMapper()));
        var event=new MqEvent("LIKE",1L,2L,1L,null);
        service.record(event); service.record(event);
        assertEquals(1,count("SELECT COUNT(*) FROM notification_failure"));
        assertEquals(0,mapper.selectById(event.getEventId()).getRetryCount());
        jdbc.update("UPDATE notification_failure SET retry_count=3,status='WAITING' WHERE event_id=?",event.getEventId());
        service.record(event);
        assertEquals("EXHAUSTED",mapper.selectById(event.getEventId()).getStatus());
        service.resolve(event.getEventId()); service.record(event);
        assertEquals("RESOLVED",mapper.selectById(event.getEventId()).getStatus());
    }
    @Test void replyRetryMigrationPreservesExistingReplyRelations() throws Exception {
        jdbc.execute("DROP TABLE notification_failure");
        jdbc.execute("ALTER TABLE comment DROP COLUMN reply_to_comment_id,DROP COLUMN reply_to_user_id,DROP INDEX idx_comment_parent_status");
        jdbc.update("INSERT INTO comment(id,post_id,author_id,parent_id,content) VALUES(10,1,1,NULL,'root'),(11,1,2,10,'child')");
        try(Connection c=dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c,new FileSystemResource("sql/migrations_2026-10-03_replies_retry.sql"));
        }
        assertEquals(10,count("SELECT reply_to_comment_id FROM comment WHERE id=11"));
        assertEquals(1,count("SELECT reply_to_user_id FROM comment WHERE id=11"));
        assertEquals(0,count("SELECT COUNT(*) FROM notification_failure"));
    }

    @Test void removingLocationAndAllImagesPersistsExplicitEmptyValues() {
        jdbc.update("UPDATE post SET longitude=120,latitude=30,images='[\"https://example.com/old.png\"]' WHERE id=1");
        Post update=new Post(); update.setTitle("edited"); update.setContent("body");
        update.setCategoryId(1); update.setImages(List.of());
        posts.updatePost(1L,update,1L);
        assertEquals(1,count("SELECT COUNT(*) FROM post WHERE id=1 AND longitude IS NULL AND latitude IS NULL AND JSON_LENGTH(images)=0"));
    }
    @Test void socialPagesPreserveRelationshipOrderAndHidePhone() {
        jdbc.update("INSERT INTO user(id,phone,nickname) VALUES(3,'13800000003','third')");
        jdbc.update("INSERT INTO user_follow(id,follower_id,followee_id) VALUES(10,1,2),(20,1,3)");
        var result=users.getFollowing(1L,1,20);
        assertEquals(List.of(3L,2L),result.getRecords().stream().map(User::getId).toList());
        assertTrue(result.getRecords().stream().allMatch(user->user.getPhone()==null && user.getPassword()==null));
        assertEquals(2,result.getTotal());
    }
    @Test void deletingAnOccupiedCategoryFailsAndUnusedCategoryCanBeRemoved() {
        var controller=new com.geocommunity.controller.AdminController();
        ReflectionTestUtils.setField(controller,"categoryMapper",sql.getMapper(CategoryMapper.class));
        ReflectionTestUtils.setField(controller,"postMapper",postMapper);
        ReflectionTestUtils.setField(controller,"postService",posts);
        var admin=transactional(controller,com.geocommunity.controller.AdminController.class);
        assertEquals(400,admin.deleteCategory(1).getCode());
        assertEquals(1,count("SELECT COUNT(*) FROM category WHERE id=1"));
        jdbc.update("INSERT INTO category(id,name) VALUES(2,'unused')");
        assertEquals(200,admin.deleteCategory(2).getCode());
        assertEquals(0,count("SELECT COUNT(*) FROM category WHERE id=2"));
    }
    @Test void notificationPageIncludesActorWithoutPrivateUserFields() throws Exception {
        sql.getMapper(NotificationMapper.class).insert(notice("actor-test",1L));
        var service=new NotificationServiceImpl();
        ReflectionTestUtils.setField(service,"notificationMapper",sql.getMapper(NotificationMapper.class));
        ReflectionTestUtils.setField(service,"userMapper",sql.getMapper(UserMapper.class));
        var page=service.listNotifications(1L,1,20);
        assertEquals("reader",page.getRecords().get(0).getFromUser().getNickname());
        String response=new ObjectMapper().findAndRegisterModules().writeValueAsString(page);
        assertFalse(response.contains("phone")); assertFalse(response.contains("password"));
    }
}
