package com.geocommunity.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.entity.Notification;
import com.geocommunity.entity.UserFollow;
import com.geocommunity.mapper.NotificationMapper;
import com.geocommunity.mapper.UserFollowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RabbitMQ 消息消费者
 * <p>
 * 当前仅处理通知队列（点赞/评论/新帖通知），
 * 浏览计数和热榜已改为 Redis 直接操作。
 */
@Component
public class MqConsumer {

    private static final Logger log = LoggerFactory.getLogger(MqConsumer.class);

    private static final int FOLLOWER_PAGE_SIZE = 1000;
    private static final int BATCH_SIZE = 500;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired private FailedNotificationService failures;

    @Autowired
    private UserFollowMapper userFollowMapper;

    // ==================== 通知队列 ====================

    @RabbitHandler
    @RabbitListener(queues = "geo.notification")
    public void handleNotification(MqEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            // 旧消息缺少稳定事件ID，隔离到死信队列，禁止静默破坏幂等。
            throw new IllegalArgumentException("通知缺少eventId，请迁移旧消息后重放");
        }
        try {
            switch (event.getType()) {
                case "NEW_POST" -> handleNewPostNotification(event);
                case "LIKE" -> handleLikeNotification(event);
                case "COMMENT" -> handleCommentNotification(event);
                case "FOLLOW" -> handleFollowNotification(event);
                default -> throw new IllegalArgumentException("未知通知事件类型: " + event.getType());
            }
            failures.resolve(event.getEventId());
        } catch (Exception e) {
            log.error("通知消费失败: type={}, postId={}", event.getType(), event.getPostId(), e);
            throw e; // 抛出异常触发重试
        }
    }

    private void handleNewPostNotification(MqEvent event) {
        Long authorId = event.getFromUserId();
        if (authorId == null) return;

        LocalDateTime now = LocalDateTime.now();
        int pageNo = 1;

        while (true) {
            // 分页查询粉丝，防止大 V 粉丝过多撑爆内存
            Page<UserFollow> followerPage = userFollowMapper.selectPage(
                    new Page<>(pageNo, FOLLOWER_PAGE_SIZE),
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFolloweeId, authorId).orderByAsc(UserFollow::getId));

            List<UserFollow> followers = followerPage.getRecords();
            if (followers.isEmpty()) break;

            // 批量插入通知（忽略重复）
            List<Notification> batch = new ArrayList<>(BATCH_SIZE);
            for (UserFollow f : followers) {
                Notification notice = new Notification();
                notice.setEventId(event.getEventId());
                notice.setUserId(f.getFollowerId());
                notice.setFromUserId(authorId);
                notice.setType("new_post");
                notice.setContent("发布了新帖子：" + event.getExtra());
                notice.setPostId(event.getPostId());
                notice.setIsRead(false);
                notice.setCreatedAt(now);
                batch.add(notice);

                if (batch.size() >= BATCH_SIZE) {
                    insertBatchQuietly(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                insertBatchQuietly(batch);
            }

            // 是否还有下一页
            if (pageNo >= followerPage.getPages()) break;
            pageNo++;
        }
    }

    private void insertBatchQuietly(List<Notification> batch) {
        try {
            notificationMapper.insertBatch(batch);
        } catch (DuplicateKeyException e) {
            // 重复通知忽略（幂等）
            log.debug("通知已存在，跳过: size={}", batch.size());
        }
    }

    private void insertQuietly(Notification notice) {
        try {
            notificationMapper.insert(notice);
        } catch (DuplicateKeyException e) {
            // 重复通知忽略（幂等）
        }
    }

    private void handleLikeNotification(MqEvent event) {
        // 自己点赞自己不发
        Long targetUserId = event.getTargetUserId();
        if (targetUserId == null || targetUserId.equals(event.getFromUserId())) {
            return;
        }
        Notification notice = new Notification();
        notice.setEventId(event.getEventId());
        notice.setUserId(event.getTargetUserId());
        notice.setFromUserId(event.getFromUserId());
        notice.setType("like");
        notice.setContent("赞了你的帖子");
        notice.setPostId(event.getPostId());
        notice.setIsRead(false);
        notice.setCreatedAt(LocalDateTime.now());
        insertQuietly(notice);
    }

    private void handleCommentNotification(MqEvent event) {
        Long targetUserId = event.getTargetUserId();
        if (targetUserId == null || targetUserId.equals(event.getFromUserId())) {
            return;
        }
        String preview = event.getExtra() != null && event.getExtra().length() > 50
                ? event.getExtra().substring(0, 50) + "..."
                : event.getExtra();
        Notification notice = new Notification();
        notice.setEventId(event.getEventId());
        notice.setUserId(event.getTargetUserId());
        notice.setFromUserId(event.getFromUserId());
        notice.setType("comment");
        notice.setContent("评论了你：" + preview);
        notice.setPostId(event.getPostId());
        notice.setIsRead(false);
        notice.setCreatedAt(LocalDateTime.now());
        insertQuietly(notice);
    }

    private void handleFollowNotification(MqEvent event) {
        Long targetUserId = event.getTargetUserId();
        if (targetUserId == null || targetUserId.equals(event.getFromUserId())) {
            return;
        }
        Notification notice = new Notification();
        notice.setEventId(event.getEventId());
        notice.setUserId(targetUserId);
        notice.setFromUserId(event.getFromUserId());
        notice.setType("follow");
        notice.setContent("关注了你");
        notice.setIsRead(false);
        notice.setCreatedAt(LocalDateTime.now());
        insertQuietly(notice);
    }

    // ==================== 死信队列 ====================

    /**
     * 通知死信消费者。
     * 保存原始死信后确认；合法事件延迟重发，无效消息保留人工处理。
     * 保存失败抛出异常，由专用容器退避后重新入队。
     */
    @RabbitHandler
    @RabbitListener(queues = "geo.notification.dlq", containerFactory="dlqContainerFactory")
    public void handleDlq(org.springframework.amqp.core.Message message) {
        failures.recordRaw(message.getBody());
        log.warn("通知死信已持久化，合法事件将延迟重试，无效事件保留人工处理");
    }
}
