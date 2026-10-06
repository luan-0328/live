package com.geocommunity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.entity.Notification;
import com.geocommunity.mapper.NotificationMapper;
import com.geocommunity.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired private com.geocommunity.mapper.UserMapper userMapper;

    // ==================== 查询 / 标记已读 ====================

    @Override
    public Page<Notification> listNotifications(Long userId, int page, int size) {
        page = Math.max(page, 1);
        size = Math.max(1, Math.min(size, 100));
        Page<Notification> pageParam = new Page<>(page, size);
        Page<Notification> result=notificationMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt).orderByDesc(Notification::getId));
        var ids=result.getRecords().stream().map(Notification::getFromUserId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        if(!ids.isEmpty()) {
            var authors=userMapper.selectBatchIds(ids).stream().collect(java.util.stream.Collectors.toMap(
                com.geocommunity.entity.User::getId,user->user));
            for(Notification notice:result.getRecords()) {
                var user=authors.get(notice.getFromUserId());
                if(user!=null && !Integer.valueOf(-1).equals(user.getStatus())) {
                    var author=new com.geocommunity.dto.AuthorVO(); author.setUserId(user.getId());
                    author.setNickname(user.getNickname()); author.setAvatar(user.getAvatar()); notice.setFromUser(author);
                }
            }
        }
        return result;
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false));
    }

    @Override
    public void markAsRead(Long notificationId, Long userId) {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, notificationId)
                        .eq(Notification::getUserId, userId)
                        .set(Notification::getIsRead, true));
    }

    @Override
    public void markAllAsRead(Long userId) {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false)
                        .set(Notification::getIsRead, true));
    }
}
