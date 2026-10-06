package com.geocommunity.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息队列事件
 * <p>
 * routingKey 决定由哪个消费者处理：
 *   notification → 通知服务消费
 *   （浏览计数和热榜已改为 Redis 直接操作）
 */
@Data
@NoArgsConstructor
public class MqEvent {
    private String eventId;

    public MqEvent(String type, Long postId, Long fromUserId, Long targetUserId, String extra) {
        this.eventId = java.util.UUID.randomUUID().toString();
        this.type = type; this.postId = postId; this.fromUserId = fromUserId;
        this.targetUserId = targetUserId; this.extra = extra;
    }
    /** 事件类型，如 LIKE / COMMENT / NEW_POST / VIEW / HOT_SCORE */
    private String type;

    /** 帖子 ID（大多数事件都关联帖子） */
    private Long postId;

    /** 触发事件的用户 ID */
    private Long fromUserId;

    /** 接收通知的用户 ID（通知事件用） */
    private Long targetUserId;

    /** 额外数据，如通知内容、分数变化等 */
    private String extra;
}
