package com.geocommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.geocommunity.dto.AuthorVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "post", autoResultMap = true)
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String content;

    @TableField("category_id")
    private Integer categoryId;

    @TableField("author_id")
    private Long authorId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;
    @TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Double longitude;
    @TableField(updateStrategy=com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Double latitude;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("comment_count")
    private Integer commentCount;

    @TableField("view_count")
    private Integer viewCount;

    @TableLogic(value = "1", delval = "-1")
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Boolean isLiked;

    @TableField(exist = false)
    private Boolean isFavorited;

    @TableField(exist = false)
    private AuthorVO author;

    @TableField(exist = false)
    private String categoryName;
}
