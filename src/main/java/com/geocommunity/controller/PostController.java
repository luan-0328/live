package com.geocommunity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.common.result.Result;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.dto.PostVO;
import com.geocommunity.entity.Comment;
import com.geocommunity.dto.PostWriteRequest;
import jakarta.validation.Valid;
import com.geocommunity.entity.Post;
import com.geocommunity.service.CommentService;
import com.geocommunity.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/post")
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;


    // ==================== 帖子列表 ====================

    /** 帖子列表（支持分类筛选、排序、分页） */
    @GetMapping("/list")
    public Result<Page<PostVO>> list(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "hot") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long authorId) {
        return Result.ok(postService.listPosts(categoryId, sort, page, size, authorId));
    }

    /** 附近帖子（基于地理位置经纬度 + 半径） */
    @GetMapping("/nearby")
    public Result<Page<PostVO>> nearby(
            @RequestParam Double longitude,
            @RequestParam Double latitude,
            @RequestParam(defaultValue = "10") Integer radius,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(postService.nearbyPosts(longitude, latitude, radius, categoryId, page, size));
    }

    /** 搜索帖子（按关键词 + 分类） */
    @GetMapping("/search")
    public Result<Page<PostVO>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(postService.searchPosts(keyword, categoryId, page, size));
    }

    // ==================== 帖子详情 / 发布 / 编辑 / 删除 ====================

    /** 帖子详情（含缓存三灾防护），同时标记当前用户是否点赞/收藏 */
    @GetMapping("/{id}")
    public Result<Post> detail(@PathVariable Long id) {
        Post post = postService.getDetail(id);
        if (post == null) {
            return Result.fail(1003, "帖子不存在");
        }
        Long userId = UserContext.get();
        if (userId != null) {
            post.setIsLiked(postService.isLiked(id, userId));
            post.setIsFavorited(postService.isFavorited(id, userId));
        }
        // 作者信息和分类名已在 PostServiceImpl.getDetail 中通过 JOIN 查询填充
        return Result.ok(post);
    }

    /** 发布帖子 */
    @PostMapping
    public Result<Post> create(@Valid @RequestBody PostWriteRequest post) {
        Long userId = UserContext.get();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        return Result.ok(postService.createPost(post.toPost(), userId));
    }

    /** 编辑帖子（仅作者可操作） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PostWriteRequest post) {
        postService.updatePost(id, post.toPost(), UserContext.get());
        return Result.ok();
    }

    /** 删除帖子（仅作者可操作，含级联清理） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.deletePost(id, UserContext.get());
        return Result.ok();
    }

    // ==================== 点赞 / 取消点赞 ====================

    /** 点赞帖子 */
    @PostMapping("/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        postService.likePost(id, UserContext.get());
        return Result.ok();
    }

    /** 取消点赞 */
    @DeleteMapping("/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        postService.unlikePost(id, UserContext.get());
        return Result.ok();
    }

    // ==================== 收藏 / 取消收藏 ====================

    /** 收藏帖子 */
    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id) {
        postService.favoritePost(id, UserContext.get());
        return Result.ok();
    }

    /** 取消收藏 */
    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavorite(@PathVariable Long id) {
        postService.unfavoritePost(id, UserContext.get());
        return Result.ok();
    }

    // ==================== 评论 ====================

    /** 发表评论 */
    @PostMapping("/{postId}/comment")
    public Result<Void> addComment(@PathVariable Long postId, @Valid @RequestBody com.geocommunity.dto.CommentWriteRequest comment) {
        commentService.addComment(postId, comment.toComment(), UserContext.get());
        return Result.ok();
    }

    /** 评论列表（按时间正序） */
    @GetMapping("/{postId}/comments")
    public Result<Page<Comment>> listComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(commentService.listComments(postId, page, size));
    }
}
