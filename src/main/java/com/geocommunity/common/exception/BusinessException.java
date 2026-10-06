package com.geocommunity.common.exception;

/**
 * 业务异常，全局异常处理器会自动捕获并返回 Result.fail(code, message)。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() {
        return code;
    }
}
