package com.geocommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("reporter_id")
    private Long reporterId;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private Long targetId;

    private String reason;
    private Integer status;

    @TableField("handle_note")
    private String handleNote;

    @TableField("handler_id")
    private Long handlerId;

    private LocalDateTime createdAt;

    @TableField("handled_at")
    private LocalDateTime handledAt;
}
