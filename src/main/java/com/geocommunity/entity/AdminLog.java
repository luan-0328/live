package com.geocommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_log")
public class AdminLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("admin_id")
    private Long adminId;

    @TableField("admin_nickname")
    private String adminNickname;

    private String action;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private Long targetId;

    private String detail;
    private String ip;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
