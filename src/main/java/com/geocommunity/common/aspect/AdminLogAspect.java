package com.geocommunity.common.aspect;

import com.geocommunity.common.utils.UserContext;
import com.geocommunity.config.annotation.AdminLog;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.AdminLogMapper;
import com.geocommunity.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 管理员操作日志切面。
 * <p>
 * 拦截所有标注 {@link AdminLog} 的方法，通过反射提取方法名和参数，
 * 结合 {@link UserContext} 获取当前管理员信息，写入 admin_log 表。
 */
@Aspect
@Component
public class AdminLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AdminLogAspect.class);

    @Autowired
    private AdminLogMapper adminLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Around("@annotation(com.geocommunity.config.annotation.AdminLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 先执行业务方法
        Object result = joinPoint.proceed();
        if(result instanceof com.geocommunity.common.result.Result<?> response && response.getCode()!=200) return result;

        // 只有管理员才记录日志（业务方法执行后再检查，避免切面影响业务异常抛出）
        Long userId = UserContext.get();
        if (userId == null) return result;

        User admin = userMapper.selectById(userId);
        if (admin == null || !"ROLE_ADMIN".equals(admin.getRole()) || admin.getNickname() == null) return result;

        try {
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            AdminLog annotation = method.getAnnotation(AdminLog.class);

            com.geocommunity.entity.AdminLog logEntry = new com.geocommunity.entity.AdminLog();
            logEntry.setAdminId(userId);
            logEntry.setAdminNickname(admin.getNickname());
            logEntry.setAction(annotation.action());

            // 通过反射提取方法参数名和值，识别目标 ID 和对象类型
            Object[] args = joinPoint.getArgs();
            Parameter[] params = method.getParameters();
            Map<String, Object> detailMap = new LinkedHashMap<>();
            String targetType = annotation.targetType();
            Long targetId = null;

            Set<String> sensitiveNames = Set.of("password", "token", "secret", "oldPassword", "newPassword", "authorization");
            for (int i = 0; i < params.length; i++) {
                String name = params[i].getName();
                Object value = sensitiveNames.contains(name) ? "***" : args[i];
                detailMap.put(name, value);

                // 尝试从 @PathVariable 参数中提取目标 ID
                if (targetId == null && value instanceof Number num && (name.contains("id") || name.contains("Id"))) {
                    targetId = num.longValue();
                }
                // 若 targetType 未指定，从参数名推断（如 userId → user, postId → post）
                if (targetType.isEmpty() && name.contains("Id")) {
                    targetType = name.replace("Id", "").replace("id", "");
                }
            }

            logEntry.setTargetType(targetType);
            logEntry.setTargetId(targetId);

            // 将参数详情序列化为 JSON 字符串（用反射避免 Jackson 依赖耦合）
            logEntry.setDetail(detailMap.toString());

            // 获取请求 IP
            try {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    HttpServletRequest req = attrs.getRequest();
                    String ip = req.getHeader("X-Forwarded-For");
                    if (ip == null || ip.isEmpty()) ip = req.getRemoteAddr();
                    logEntry.setIp(ip);
                }
            } catch (Exception ignored) {}

            logEntry.setCreatedAt(LocalDateTime.now());
            adminLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 日志记录失败不影响主流程
            log.warn("记录管理员操作日志失败", e);
        }

        return result;
    }
}
