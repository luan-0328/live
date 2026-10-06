package com.geocommunity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.entity.Report;

public interface ReportService {

    void submitReport(String targetType, Long targetId, String reason, Long userId);

    Page<Report> listReports(Integer status, int page, int size);

    void handleReport(Long reportId, Integer status, String handleNote, Long adminId);

    Page<Report> myReports(Long userId, int page, int size);

    long countPending();
}
