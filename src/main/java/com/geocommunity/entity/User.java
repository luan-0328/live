package com.geocommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String nickname;
    private String avatar;
    private String role;

    @TableField("follower_count")
    private Integer followerCount;

    @TableField("following_count")
    private Integer followingCount;

    @TableField("post_count")
    private Integer postCount;

    @TableField(exist = false)
    private Boolean isFollowing;

    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
