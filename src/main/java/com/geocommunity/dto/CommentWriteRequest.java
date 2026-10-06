package com.geocommunity.dto;

import com.geocommunity.entity.Comment;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CommentWriteRequest {
    @NotBlank @Size(max=500) private String content;
    @Positive private Long parentId;
    @Positive private Long replyToCommentId;
    public Comment toComment() {
        Comment comment=new Comment(); comment.setContent(content);
        comment.setParentId(parentId); comment.setReplyToCommentId(replyToCommentId); return comment;
    }
}
