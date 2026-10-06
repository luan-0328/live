package com.geocommunity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.entity.Notification;

public interface NotificationService {

    /**
     * 获取用户的通知列表
     */
    Page<Notification> listNotifications(Long userId, int page, int size);

    /**
     * 未读通知数
     */
    long countUnread(Long userId);

    /**
     * 标记单条已读
     */
    void markAsRead(Long notificationId, Long userId);

    /**
     * 标记全部已读
     */
    void markAllAsRead(Long userId);
}
