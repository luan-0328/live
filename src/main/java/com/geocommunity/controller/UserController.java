package com.geocommunity.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geocommunity.common.result.Result;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.dto.PostVO;
import com.geocommunity.dto.UpdatePasswordRequest;
import com.geocommunity.dto.UpdatePhoneRequest;
import com.geocommunity.dto.UpdateProfileRequest;
import com.geocommunity.entity.User;
import com.geocommunity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    // ==================== 用户信息 ====================

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public Result<User> me() {
        Long userId = UserContext.get();
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail(401, "未登录");
        }
        user.setPhone(StrUtil.hide(user.getPhone(), 3, 7));
        return Result.ok(user);
    }

    /** 获取指定用户公开信息（含是否已关注标记） */
    @GetMapping("/{userId}/profile")
    public Result<User> profile(@PathVariable Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.fail(1003, "用户不存在");
        }
        user.setPhone(StrUtil.hide(user.getPhone(), 3, 7));
        Long currentUserId = UserContext.get();
        if (currentUserId != null && !currentUserId.equals(userId)) {
            user.setIsFollowing(userService.isFollowing(currentUserId, userId));
        }
        return Result.ok(user);
    }

    /** 修改昵称/头像 */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        userService.updateProfile(UserContext.get(), req);
        return Result.ok();
    }

    /** 修改绑定手机号（需短信验证码） */
    @PutMapping("/phone")
    public Result<Void> updatePhone(@Valid @RequestBody UpdatePhoneRequest req) {
        userService.updatePhone(UserContext.get(), req);
        return Result.ok();
    }

    /** 修改密码 */
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest req) {
        userService.updatePassword(UserContext.get(), req);
        return Result.ok();
    }

    /** 注销账号 */
    @DeleteMapping("/account")
    public Result<Void> deleteAccount() {
        userService.deleteAccount(UserContext.get());
        return Result.ok();
    }

    // ==================== 关注 / 取消关注 ====================

    /** 关注用户 */
    @PostMapping("/follow/{userId}")
    public Result<Void> follow(@PathVariable Long userId) {
        userService.followUser(UserContext.get(), userId);
        return Result.ok();
    }

    /** 取消关注 */
    @DeleteMapping("/follow/{userId}")
    public Result<Void> unfollow(@PathVariable Long userId) {
        userService.unfollowUser(UserContext.get(), userId);
        return Result.ok();
    }

    // ==================== 粉丝 / 关注列表 ====================

    /** 我的粉丝列表 */
    @GetMapping("/followers")
    public Result<Page<User>> followers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.getFollowers(UserContext.get(), page, size));
    }

    /** 我的关注列表 */
    @GetMapping("/following")
    public Result<Page<User>> following(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.getFollowing(UserContext.get(), page, size));
    }

    // ==================== 我的收藏 ====================

    /** 我的收藏列表 */
    @GetMapping("/favorites")
    public Result<Page<PostVO>> favorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.getFavorites(UserContext.get(), page, size));
    }
}
