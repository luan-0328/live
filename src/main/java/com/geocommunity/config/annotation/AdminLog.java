package com.geocommunity.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要记录管理员操作日志的方法。
 * <p>
 * 通过 AOP + 反射自动提取方法参数写入 admin_log 表，
 * 仅当当前用户为管理员时生效。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminLog {

    /** 操作描述，如"封禁用户"、"删除帖子" */
    String action();

    /** 操作对象类型（可选），如 "user" / "post" / "category" / "report" */
    String targetType() default "";
}
