package com.geocommunity.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notification_failure")
public class FailedNotification {
    @TableId private String eventId;
    private String payload;
    private Integer retryCount;
    private String status;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime createdAt;
}
