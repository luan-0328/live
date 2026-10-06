package com.geocommunity.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 AdminController 的方法上，自动校验当前用户是否为管理员。
 * <p>
 * 非管理员访问会被拦截并抛出 ForbiddenException，由全局异常处理器返回 403。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {
}
