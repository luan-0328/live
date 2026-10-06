package com.geocommunity.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.entity.Comment;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.CommentMapper;
import com.geocommunity.mapper.PostMapper;
import com.geocommunity.mapper.UserMapper;
import com.geocommunity.common.exception.BusinessException;
import com.geocommunity.mq.MqEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentMapper commentMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private com.geocommunity.mq.OutboxService outboxService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private CommentServiceImpl commentService;

    @Captor
    private ArgumentCaptor<MqEvent> mqCaptor;

    private Post mockPost;
    private final Long postId = 1L;
    private final Long userId = 100L;
    private final Long authorId = 200L;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        mockPost = new Post();
        mockPost.setId(postId);
        mockPost.setAuthorId(authorId);
        mockPost.setTitle("测试帖");
        mockPost.setStatus(1);
    }

    @Test
    void addComment_shouldSucceed() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Comment input = new Comment();
        input.setContent("好帖子！");

        Comment result = commentService.addComment(postId, input, userId);

        assertNotNull(result);
        assertEquals(postId, result.getPostId());
        assertEquals(userId, result.getAuthorId());
        assertEquals(1, result.getStatus().intValue());
        assertNotNull(result.getCreatedAt());

        verify(commentMapper).insert(any(Comment.class));
        verify(postMapper).update(any(), any());
        // 热榜走 Redis，MQ 只发通知
        verify(outboxService).enqueue(any(MqEvent.class));
        verify(zSetOperations).incrementScore("post:hot:24h", String.valueOf(postId), 3);
    }

    @Test
    void addComment_shouldThrow_whenPostNotExist() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(null);

        Comment input = new Comment();
        input.setContent("好帖子！");

        assertThrows(BusinessException.class,
                () -> commentService.addComment(postId, input, userId));
    }

    @Test
    void addComment_shouldThrow_whenPostDeleted() {
        mockPost.setStatus(0);
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);

        Comment input = new Comment();
        input.setContent("好帖子！");

        assertThrows(BusinessException.class,
                () -> commentService.addComment(postId, input, userId));
    }

    @Test
    void addComment_shouldSendNotificationViaMq() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        Comment input = new Comment();
        input.setContent("写的真棒！");
        commentService.addComment(postId, input, userId);

        // 只发一条通知 MQ，热榜已改为 Redis
        verify(outboxService).enqueue(mqCaptor.capture());
        MqEvent event = mqCaptor.getValue();
        assertEquals("COMMENT", event.getType());
        assertEquals(authorId, event.getTargetUserId());
    }

    @Test
    void deleteComment_shouldSucceed() {
        Comment existing = new Comment();
        existing.setId(10L);
        existing.setPostId(postId);
        existing.setAuthorId(userId);
        existing.setStatus(1);

        when(commentMapper.selectById(10L)).thenReturn(existing);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(commentMapper.deleteById(10L)).thenReturn(1);
        commentService.deleteComment(10L, userId);

        verify(commentMapper).deleteById(10L);
        verify(postMapper).update(any(), any());
        // 热榜改为 Redis ZSET
        verify(zSetOperations).incrementScore("post:hot:24h", String.valueOf(postId), -3);
    }

    @Test
    void deleteComment_shouldThrow_whenNotOwner() {
        Comment existing = new Comment();
        existing.setId(10L);
        existing.setPostId(postId);
        existing.setAuthorId(authorId);
        existing.setStatus(1);

        when(commentMapper.selectById(10L)).thenReturn(existing);

        assertThrows(BusinessException.class,
                () -> commentService.deleteComment(10L, userId));
    }

    @Test
    void listComments_shouldLoadAuthorInfo() {
        Comment c1 = new Comment();
        c1.setId(1L);
        c1.setPostId(postId);
        c1.setAuthorId(userId);
        c1.setContent("评论1");
        c1.setStatus(1);
        c1.setCreatedAt(LocalDateTime.now());

        User author = new User();
        author.setId(userId);
        author.setNickname("测试用户");
        author.setAvatar("http://example.com/avatar.png");

        Page<Comment> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(c1));
        mockPage.setTotal(1);

        when(commentMapper.selectRoots(any(), anyLong())).thenReturn(mockPage);
        when(userMapper.selectBatchIds(List.of(userId))).thenReturn(List.of(author));

        Page<Comment> result = commentService.listComments(postId, 1, 20);

        assertEquals(1, result.getRecords().size());
        assertNotNull(result.getRecords().get(0).getAuthor());
        assertEquals("测试用户", result.getRecords().get(0).getAuthor().getNickname());
        assertEquals(userId, result.getRecords().get(0).getAuthor().getUserId());
    }

    @Test void replyToChildRemainsInRootAndNotifiesActualAuthor() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        Comment root=existing(10L,null,authorId), child=existing(11L,10L,300L);
        when(commentMapper.selectById(11L)).thenReturn(child);
        when(commentMapper.selectById(10L)).thenReturn(root);
        Comment input=new Comment(); input.setContent("reply"); input.setParentId(10L);
        input.setReplyToCommentId(11L); input.setReplyToUserId(999L);
        Comment result=commentService.addComment(postId,input,userId);
        assertEquals(10L,result.getParentId()); assertEquals(11L,result.getReplyToCommentId());
        assertEquals(300L,result.getReplyToUserId());
        verify(outboxService).enqueue(argThat(event->event.getTargetUserId().equals(300L)));
    }
    @Test void crossPostReplyIsRejectedWithoutInsertOrNotification() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        Comment target=existing(10L,null,authorId); target.setPostId(2L);
        when(commentMapper.selectById(10L)).thenReturn(target);
        Comment input=new Comment(); input.setContent("reply"); input.setParentId(10L);
        assertThrows(BusinessException.class,()->commentService.addComment(postId,input,userId));
        verify(commentMapper,never()).insert(any(Comment.class)); verifyNoInteractions(outboxService);
    }
    @Test void deletedRootCannotReceiveNewChildReplies() {
        when(postMapper.selectActiveForUpdate(postId)).thenReturn(mockPost);
        when(commentMapper.selectById(11L)).thenReturn(existing(11L,10L,300L));
        when(commentMapper.selectById(10L)).thenReturn(null);
        Comment input=new Comment(); input.setContent("reply"); input.setReplyToCommentId(11L);
        assertThrows(BusinessException.class,()->commentService.addComment(postId,input,userId));
        verify(commentMapper,never()).insert(any(Comment.class));
    }
    private Comment existing(Long id,Long rootId,Long author) {
        Comment c=new Comment(); c.setId(id); c.setPostId(postId); c.setParentId(rootId);
        c.setAuthorId(author); c.setStatus(1); return c;
    }
}
