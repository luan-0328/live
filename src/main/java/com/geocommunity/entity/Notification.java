package com.geocommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @com.baomidou.mybatisplus.annotation.TableField(exist=false)
    private com.geocommunity.dto.AuthorVO fromUser;
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;

    @TableField("user_id")
    private Long userId;

    @TableField("from_user_id")
    private Long fromUserId;

    private String type;
    private String content;

    @TableField("post_id")
    private Long postId;

    @TableField("is_read")
    private Boolean isRead;

    private LocalDateTime createdAt;
}
