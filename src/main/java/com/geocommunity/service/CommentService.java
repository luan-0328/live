package com.geocommunity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.entity.Comment;

public interface CommentService {

    Comment addComment(Long postId, Comment comment, Long userId);

    void deleteComment(Long commentId, Long userId);

    boolean removeComment(Long commentId);

    Page<Comment> listComments(Long postId, int page, int size);
}
