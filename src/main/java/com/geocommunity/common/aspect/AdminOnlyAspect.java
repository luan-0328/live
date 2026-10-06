package com.geocommunity.common.aspect;

import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.exception.BusinessException;
import com.geocommunity.common.exception.ForbiddenException;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 拦截 {@link AdminOnly} 注解，校验当前用户是否为管理员。
 * <p>
 * 除角色外还校验 Redis 会话存活：管理员登出（session 已删）后，
 * 即使 JWT 未过期（GET 请求会被 AuthInterceptor 放行），也不得访问后台接口。
 */
@Aspect
@Component
public class AdminOnlyAspect {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Before("@annotation(com.geocommunity.config.annotation.AdminOnly)")
    public void checkAdmin() {
        Long userId = UserContext.get();
        if (userId == null) {
            throw new ForbiddenException("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !"ROLE_ADMIN".equals(user.getRole()) || user.getStatus() != 1) {
            throw new ForbiddenException("无管理员权限");
        }

        // 会话校验：登出后 session 已删除 → 401，前端自动登出
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    String idleKey = RedisKeys.SESSION_IDLE + header.substring(7);
                    if (Boolean.FALSE.equals(redisTemplate.hasKey(idleKey))) {
                        throw new BusinessException(401, "登录已过期");
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception ignored) {
            throw new BusinessException(401, "会话服务暂不可用，请稍后重试");
        }
    }
}
