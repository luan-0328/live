package com.geocommunity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.common.exception.BusinessException;
import com.geocommunity.entity.Comment;
import com.geocommunity.entity.Post;
import com.geocommunity.entity.Report;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.CommentMapper;
import com.geocommunity.mapper.PostMapper;
import com.geocommunity.mapper.ReportMapper;
import com.geocommunity.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportMapper reportMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ReportServiceImpl reportService;

    private final Long userId = 100L;
    private final Long adminId = 200L;
    private final Long targetId = 1L;

    private Post mockPost() {
        Post p = new Post();
        p.setId(targetId);
        p.setStatus(1);
        return p;
    }

    private Comment mockComment() {
        Comment c = new Comment();
        c.setId(targetId);
        c.setStatus(1);
        return c;
    }

    private User mockUser() {
        User u = new User();
        u.setId(targetId);
        u.setStatus(1);
        return u;
    }

    private Report mockReport(Integer status) {
        Report r = new Report();
        r.setId(1L);
        r.setReporterId(userId);
        r.setTargetType("post");
        r.setTargetId(targetId);
        r.setReason("广告");
        r.setStatus(status);
        return r;
    }

    // ==================== 提交举报 ====================

    @Test
    void submitReport_shouldSucceed_withPostTarget() {
        when(postMapper.selectById(targetId)).thenReturn(mockPost());
        when(reportMapper.selectOne(any())).thenReturn(null);

        reportService.submitReport("post", targetId, "广告", userId);

        verify(reportMapper).insert(any(Report.class));
    }

    @Test
    void submitReport_shouldSucceed_withCommentTarget() {
        when(commentMapper.selectById(targetId)).thenReturn(mockComment());
        when(reportMapper.selectOne(any())).thenReturn(null);

        reportService.submitReport("comment", targetId, "辱骂", userId);

        verify(reportMapper).insert(any(Report.class));
    }

    @Test
    void submitReport_shouldSucceed_withUserTarget() {
        when(userMapper.selectById(targetId)).thenReturn(mockUser());
        when(reportMapper.selectOne(any())).thenReturn(null);

        reportService.submitReport("user", targetId, "骚扰", userId);

        verify(reportMapper).insert(any(Report.class));
    }

    @Test
    void submitReport_shouldThrow_whenInvalidTargetType() {
        assertThrows(BusinessException.class,
                () -> reportService.submitReport("invalid", targetId, "理由", userId));
        verify(reportMapper, never()).insert(any(Report.class));
    }

    @Test
    void submitReport_shouldThrow_whenPostNotExist() {
        when(postMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> reportService.submitReport("post", 999L, "广告", userId));
        verify(reportMapper, never()).insert(any(Report.class));
    }

    @Test
    void submitReport_shouldThrow_whenCommentNotExist() {
        when(commentMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> reportService.submitReport("comment", 999L, "辱骂", userId));
        verify(reportMapper, never()).insert(any(Report.class));
    }

    @Test
    void submitReport_shouldThrow_whenUserNotExist() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> reportService.submitReport("user", 999L, "骚扰", userId));
        verify(reportMapper, never()).insert(any(Report.class));
    }

    @Test
    void submitReport_shouldThrow_whenDuplicate() {
        when(postMapper.selectById(targetId)).thenReturn(mockPost());
        when(reportMapper.selectOne(any())).thenReturn(new Report());

        assertThrows(BusinessException.class,
                () -> reportService.submitReport("post", targetId, "广告", userId));
        verify(reportMapper, never()).insert(any(Report.class));
    }

    // ==================== 处理举报 ====================

    @Test
    void handleReport_shouldThrow_whenReportNotExist() {
        when(reportMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> reportService.handleReport(999L, 1, "已处理", adminId));
    }

    @Test
    void handleReport_shouldThrow_whenAlreadyHandled() {
        when(reportMapper.selectById(1L)).thenReturn(mockReport(1));

        assertThrows(BusinessException.class,
                () -> reportService.handleReport(1L, 1, "已处理", adminId));
    }

    @Test
    void handleReport_shouldThrow_whenInvalidStatus() {
        assertThrows(BusinessException.class,
                () -> reportService.handleReport(1L, 3, "无效", adminId));
    }

    // ==================== 查询 ====================

    @Test
    void countPending_shouldReturnCount() {
        when(reportMapper.selectCount(any())).thenReturn(5L);
        assertEquals(5L, reportService.countPending());
    }

    @Test
    void myReports_shouldReturnPagedResults() {
        Page<Report> mockPage = new Page<>(1, 20);
        mockPage.setTotal(1);
        when(reportMapper.selectPage(any(), any())).thenReturn(mockPage);

        Page<Report> result = reportService.myReports(userId, 1, 20);
        assertEquals(1, result.getTotal());
    }

    @Test
    void listReports_shouldFilterByStatus() {
        Page<Report> mockPage = new Page<>(1, 20);
        when(reportMapper.selectPage(any(), any())).thenReturn(mockPage);

        reportService.listReports(1, 1, 20);
        verify(reportMapper).selectPage(any(), any());
    }
}
