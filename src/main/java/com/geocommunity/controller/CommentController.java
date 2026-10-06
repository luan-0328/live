package com.geocommunity.controller;

import com.geocommunity.common.result.Result;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @DeleteMapping("/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId, UserContext.get());
        return Result.ok();
    }
}
