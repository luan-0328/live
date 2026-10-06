package com.geocommunity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.geocommunity.dto.PostVO;
import com.geocommunity.dto.UpdatePasswordRequest;
import com.geocommunity.dto.UpdatePhoneRequest;
import com.geocommunity.dto.UpdateProfileRequest;
import com.geocommunity.entity.User;

public interface UserService extends IService<User> {

    // ==================== 已有方法 ====================

    User findByPhone(String phone);

    boolean isPhoneExists(String phone);

    User findAdmin(String account, String password);

    // ==================== 用户信息 ====================

    void updateProfile(Long userId, UpdateProfileRequest req);

    void updatePhone(Long userId, UpdatePhoneRequest req);

    void updatePassword(Long userId, UpdatePasswordRequest req);

    void deleteAccount(Long userId);

    // ==================== 关注 / 取消关注 ====================

    void followUser(Long currentUserId, Long targetUserId);

    void unfollowUser(Long currentUserId, Long targetUserId);

    Page<User> getFollowers(Long userId, int page, int size);

    Page<User> getFollowing(Long userId, int page, int size);

    boolean isFollowing(Long currentUserId, Long targetUserId);

    // ==================== 收藏列表 ====================

    Page<PostVO> getFavorites(Long userId, int page, int size);
}
