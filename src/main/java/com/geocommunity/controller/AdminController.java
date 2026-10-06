package com.geocommunity.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.config.annotation.AdminLog;
import com.geocommunity.config.annotation.AdminOnly;
import com.geocommunity.common.auth.SessionService;
import com.geocommunity.common.utils.AfterCommit;
import com.geocommunity.common.utils.CacheVersion;
import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.result.Result;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.dto.HandleReportRequest;
import com.geocommunity.entity.Category;
import com.geocommunity.entity.Comment;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.PostFavorite;
import com.geocommunity.entity.PostLike;
import com.geocommunity.entity.Report;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.*;
import jakarta.validation.Valid;
import com.geocommunity.service.PostService;
import com.geocommunity.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PostService postService;

    @Autowired private com.geocommunity.service.CommentService commentService;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private PostFavoriteMapper postFavoriteMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private AdminLogMapper adminLogMapper;

    // ==================== 数据看板 ====================

    /** 数据统计（用户数/帖子数/待处理举报数/今日发帖数） */
    @AdminOnly
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long userCount = userMapper.selectCount(null);
        Long postCount = postMapper.selectCount(null);
        Long pendingReports = reportService.countPending();
        Long todayPosts = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .ge(Post::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)));

        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userCount);
        stats.put("postCount", postCount);
        stats.put("pendingReports", pendingReports);
        stats.put("todayPosts", todayPosts);
        return Result.ok(stats);
    }

    // ==================== 用户管理 ====================

    /** 用户列表（按注册时间倒序） */
    @AdminOnly
    @GetMapping("/users")
    public Result<Page<User>> listUsers(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        return Result.ok(userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt)));
    }

    /** 设置封禁/解封状态，重复请求不反转状态。 */
    @AdminOnly
    @AdminLog(action = "封禁/解封用户", targetType = "user")
    @PutMapping("/user/{userId}/ban")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> banUser(@PathVariable Long userId, @Valid @RequestBody com.geocommunity.dto.UserStatusRequest req) {
        User user = userMapper.selectForUpdate(userId);
        if (user == null) {
            return Result.fail(400, "用户不存在");
        }
        if (Integer.valueOf(-1).equals(user.getStatus())) return Result.fail(400, "注销账号不能解封");
        int newStatus = req.getStatus();
        if(newStatus==0 && userId.equals(UserContext.get())) return Result.fail(400,"不能封禁当前管理员账号");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, newStatus));
        AfterCommit.run(() -> {
            CacheVersion.invalidate(redisTemplate, RedisKeys.USER_CACHE + userId, RedisKeys.USER_CACHE + userId);
            redisTemplate.delete(RedisKeys.USER_NULL + userId);
            if (newStatus == 0) sessionService.forceLogout(userId);
        });
        // 封禁时强制下线：删除该用户全部活跃会话，其 JWT 随即失效 → 下次请求即 401 自动登出

        return Result.ok();
    }

    // ==================== 帖子管理 ====================

    /** 帖子列表（可查看所有帖子，含已删除） */
    @AdminOnly
    @GetMapping("/posts")
    public Result<Page<Post>> listPosts(
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        return Result.ok(postMapper.selectAdminPosts(new Page<>(page, size), authorId, categoryId));
    }

    /** 强制删除帖子（含级联清理点赞/收藏/评论 + 热榜 + Redis 集合） */
    @AdminOnly
    @AdminLog(action = "强制删除帖子", targetType = "post")
    @DeleteMapping("/post/{id}")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> forceDeletePost(@PathVariable Long id) {
        if (!postService.removePost(id)) return Result.fail(1003, "帖子不存在");
        return Result.ok();
    }

    @AdminOnly
    @AdminLog(action="强制删除评论",targetType="comment")
    @DeleteMapping("/comment/{id}")
    public Result<Void> forceDeleteComment(@PathVariable Long id) {
        return commentService.removeComment(id)?Result.ok():Result.fail(1003,"评论不存在或已删除");
    }

    // ==================== 分类管理 ====================

    /** 新增分类 */
    @AdminOnly
    @AdminLog(action = "新增分类", targetType = "category")
    @PostMapping("/category")
    public Result<Void> createCategory(@Valid @RequestBody com.geocommunity.dto.CategoryWriteRequest request) {
        Category category=request.toCategory();
        category.setId(null);
        category.setCreatedAt(LocalDateTime.now());
        categoryMapper.insert(category);
        return Result.ok();
    }

    /** 编辑分类 */
    @AdminOnly
    @AdminLog(action = "编辑分类", targetType = "category")
    @PutMapping("/category/{id}")
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public Result<Void> updateCategory(@PathVariable Integer id, @Valid @RequestBody com.geocommunity.dto.CategoryWriteRequest request) {
        Category category=request.toCategory();
        if (categoryMapper.selectForUpdate(id) == null) {
            return Result.fail(1010, "分类不存在");
        }
        category.setId(id);
        categoryMapper.updateById(category);
        // 列表缓存 + 详情缓存（详情内嵌分类名，TTL 30~40min）都需失效
        postService.evictPostListCache();
        postService.evictPostDetailByCategory(id);
        return Result.ok();
    }

    /** 删除分类 */
    @AdminOnly
    @AdminLog(action = "删除分类", targetType = "category")
    @DeleteMapping("/category/{id}")
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public Result<Void> deleteCategory(@PathVariable Integer id) {
        if (categoryMapper.selectForUpdate(id) == null) {
            return Result.fail(1010, "分类不存在");
        }
        if(postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getCategoryId,id))>0)
            return Result.fail(400,"分类下仍有帖子，请先调整帖子分类");
        categoryMapper.deleteById(id);
        // 列表缓存 + 详情缓存（详情内嵌分类名，TTL 30~40min）都需失效
        postService.evictPostListCache();
        postService.evictPostDetailByCategory(id);
        return Result.ok();
    }

    // ==================== 举报管理 ====================

    /** 举报列表（可按状态筛选） */
    @AdminOnly
    @GetMapping("/reports")
    public Result<Page<Report>> listReports(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        return Result.ok(reportService.listReports(status, page, size));
    }

    /** 处理举报（status=1 违规成立自动删帖/封号，status=2 驳回） */
    @AdminOnly
    @AdminLog(action = "处理举报", targetType = "report")
    @PutMapping("/report/{id}/handle")
    public Result<Void> handleReport(@PathVariable Long id,
                                     @Valid @RequestBody HandleReportRequest req) {
        reportService.handleReport(id, req.getStatus(), req.getHandleNote(), UserContext.get());
        return Result.ok();
    }

    // ==================== 操作日志 ====================

    /** 管理员操作日志 */
    @AdminOnly
    @GetMapping("/logs")
    public Result<Page<com.geocommunity.entity.AdminLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        return Result.ok(adminLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<com.geocommunity.entity.AdminLog>().orderByDesc(com.geocommunity.entity.AdminLog::getCreatedAt)));
    }
}
