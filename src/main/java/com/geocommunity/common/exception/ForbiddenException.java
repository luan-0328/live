package com.geocommunity.common.exception;

/**
 * 无权限访问，全局异常处理器自动返回 403。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
