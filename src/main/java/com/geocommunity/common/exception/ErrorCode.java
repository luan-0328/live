package com.geocommunity.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    SUCCESS(200, "success"),
    PARAM_INVALID(400, "参数校验失败"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    RATE_LIMITED(429, "请求频繁"),
    SERVER_ERROR(500, "服务器错误"),

    CODE_EXPIRED(1001, "验证码错误或过期"),
    ACCOUNT_BANNED(1002, "账号已封禁"),
    POST_NOT_FOUND(1003, "帖子不存在"),
    COMMENT_NOT_FOUND(1004, "评论不存在"),
    CONTENT_VIOLATION(1005, "内容违规"),
    DUPLICATE_OPERATION(1006, "不能重复操作"),
    ILLEGAL_OPERATION(1007, "操作非法"),
    NO_PERMISSION(1008, "无操作权限"),
    PHONE_EXISTS(1009, "手机号已注册"),
    CATEGORY_NOT_FOUND(1010, "分类不存在");

    private final int code;
    private final String message;
}
