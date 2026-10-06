package com.geocommunity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.common.result.Result;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.dto.SubmitReportRequest;
import com.geocommunity.entity.Report;
import com.geocommunity.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    /** 提交举报 */
    @PostMapping
    public Result<Void> submit(@Valid @RequestBody SubmitReportRequest req) {
        Long userId = UserContext.get();
        if (userId == null) {
            return Result.fail(401, "未登录");
        }
        reportService.submitReport(req.getTargetType(), req.getTargetId(), req.getReason(), userId);
        return Result.ok();
    }

    /** 我的举报记录 */
    @GetMapping("/my")
    public Result<Page<Report>> myReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(reportService.myReports(UserContext.get(), page, size));
    }
}
