package com.geocommunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.geocommunity.dto.AuthorVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("post_id")
    private Long postId;

    @TableField("author_id")
    private Long authorId;

    @TableField("parent_id")
    private Long parentId;

    private Long replyToCommentId;
    private Long replyToUserId;
    @TableField(exist = false)
    private AuthorVO replyToAuthor;

    private String content;
    @TableLogic(value = "1", delval = "-1")
    private Integer status;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private AuthorVO author;

    @TableField(exist = false)
    private List<Comment> children;
}
