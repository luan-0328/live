package com.geocommunity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.common.result.Result;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.entity.Notification;
import com.geocommunity.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * 通知列表
     */
    @GetMapping("/list")
    public Result<Page<Notification>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.listNotifications(UserContext.get(), page, size));
    }

    /**
     * 未读通知数
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notificationService.countUnread(UserContext.get()));
    }

    /**
     * 标记单条已读
     */
    @PutMapping({"/read/{id}", "/{id}/read"})
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markAsRead(id, UserContext.get());
        return Result.ok();
    }

    /**
     * 标记全部已读
     */
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllAsRead(UserContext.get());
        return Result.ok();
    }
}
