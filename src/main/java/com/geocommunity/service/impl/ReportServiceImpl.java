package com.geocommunity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.common.auth.SessionService;
import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.utils.AfterCommit;
import com.geocommunity.common.utils.CacheVersion;
import com.geocommunity.common.exception.BusinessException;
import com.geocommunity.entity.Comment;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.Report;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.CommentMapper;
import com.geocommunity.mapper.PostMapper;
import com.geocommunity.mapper.ReportMapper;
import com.geocommunity.mapper.UserMapper;
import com.geocommunity.service.PostService;
import com.geocommunity.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class ReportServiceImpl implements ReportService {

    private static final Set<String> VALID_TARGET_TYPES = Set.of("post", "comment", "user");

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PostService postService;

    @Autowired
    private SessionService sessionService;

    @Override
    public void submitReport(String targetType, Long targetId, String reason, Long userId) {
        if (!VALID_TARGET_TYPES.contains(targetType)) {
            throw new BusinessException(400, "举报类型无效，仅支持 post / comment / user");
        }

        // 验证举报对象存在
        switch (targetType) {
            case "post" -> {
                Post post = postMapper.selectById(targetId);
                if (post == null || post.getStatus() != 1) {
                    throw new BusinessException(1003, "举报的帖子不存在或已删除");
                }
            }
            case "comment" -> {
                Comment comment = commentMapper.selectById(targetId);
                if (comment == null || comment.getStatus() != 1) {
                    throw new BusinessException(1004, "举报的评论不存在或已删除");
                }
            }
            case "user" -> {
                User user = userMapper.selectById(targetId);
                if (user == null) {
                    throw new BusinessException(400, "举报的用户不存在");
                }
            }
        }

        // 同一用户对同一对象的待处理举报只能有一条
        Report exists = reportMapper.selectOne(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, userId)
                        .eq(Report::getTargetType, targetType)
                        .eq(Report::getTargetId, targetId)
                        .eq(Report::getStatus, 0));
        if (exists != null) {
            throw new BusinessException(1006, "已举报过该内容，请等待处理");
        }

        Report report = new Report();
        report.setReporterId(userId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setStatus(0);
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
    }

    @Override
    public Page<Report> listReports(Integer status, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        Page<Report> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .orderByDesc(Report::getCreatedAt);
        if (status != null) {
            wrapper.eq(Report::getStatus, status);
        }
        return reportMapper.selectPage(pageParam, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleReport(Long reportId, Integer status, String handleNote, Long adminId) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(400, "处理结果无效，1=违规成立 2=驳回");
        }

        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(400, "举报不存在");
        }
        if (report.getStatus() != 0) {
            throw new BusinessException(1006, "该举报已处理");
        }

        int changed = reportMapper.update(null, new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, reportId).eq(Report::getStatus, 0)
                .set(Report::getStatus, status).set(Report::getHandleNote, handleNote)
                .set(Report::getHandlerId, adminId).set(Report::getHandledAt, LocalDateTime.now()));
        if (changed != 1) throw new BusinessException(1006, "该举报已处理");

        // status=1 已处理（违规成立），自动处理被举报对象
        if (status == 1) {
            switch (report.getTargetType()) {
                case "post" -> postService.removePost(report.getTargetId());
                case "comment" -> {
                    Comment c = commentMapper.selectById(report.getTargetId());
                    if (c != null && c.getPostId() != null && postMapper.selectActiveForUpdate(c.getPostId()) != null
                            && commentMapper.deleteById(c.getId()) == 1) {
                        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, c.getPostId())
                                .setSql("comment_count = GREATEST(CAST(comment_count AS SIGNED) - 1, 0)"));
                        AfterCommit.run(() -> {
                            CacheVersion.invalidate(redisTemplate, RedisKeys.POST_DETAIL + c.getPostId(), RedisKeys.POST_DETAIL + c.getPostId());
                            redisTemplate.opsForZSet().incrementScore(RedisKeys.POST_HOT_BOARD, String.valueOf(c.getPostId()), -3);
                        });
                        postService.evictPostListCache();
                    }
                }
                case "user" -> {
                    userMapper.update(null, new LambdaUpdateWrapper<User>()
                            .eq(User::getId, report.getTargetId())
                            .ne(User::getStatus, -1)
                            .set(User::getStatus, 0));
                    AfterCommit.run(() -> {
                        CacheVersion.invalidate(redisTemplate, RedisKeys.USER_CACHE + report.getTargetId(), RedisKeys.USER_CACHE + report.getTargetId());
                        redisTemplate.delete(RedisKeys.USER_NULL + report.getTargetId());
                        sessionService.forceLogout(report.getTargetId());
                    });
                    postService.evictPostListCache();
                }
            }
        }


    }

    @Override
    public long countPending() {
        return reportMapper.selectCount(new LambdaQueryWrapper<Report>().eq(Report::getStatus, 0));
    }

    @Override
    public Page<Report> myReports(Long userId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        Page<Report> pageParam = new Page<>(page, size);
        return reportMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getReporterId, userId)
                        .orderByDesc(Report::getCreatedAt));
    }
}
