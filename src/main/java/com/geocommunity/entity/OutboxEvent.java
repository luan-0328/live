package com.geocommunity.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification_outbox")
public class OutboxEvent {
    @TableId private String id;
    private String payload;
    private LocalDateTime createdAt;
}
