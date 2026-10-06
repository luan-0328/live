package com.geocommunity.common.auth;

import com.geocommunity.common.utils.JwtUtil;
import com.geocommunity.common.utils.UserContext;
import com.geocommunity.entity.User;
import com.geocommunity.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RefreshInterceptor implements HandlerInterceptor {
    @Autowired private JwtUtil jwtUtil;
    @Autowired private SessionService sessionService;
    @Autowired private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return true;
        String token = header.substring(7);
        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            if (!sessionService.isActive(token)) return true;
            // 数据库状态是最终权限依据，Redis故障、登录与封禁竞争不能绕过封禁。
            User user = userMapper.selectById(userId);
            if (user == null || !Integer.valueOf(1).equals(user.getStatus())) return true;
            UserContext.set(userId);
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining <= 0) { UserContext.clear(); return true; }
            // 登出请求不产生新会话。旧 token 保留短暂宽限供并发请求完成。
            if (remaining < 86400000L && !"/auth/logout".equals(request.getServletPath())
                    && !"/auth/logout".equals(request.getRequestURI())) {
                String refreshed = sessionService.rotate(token, userId,
                        jwtUtil.createToken(userId, user.getRole()), jwtUtil.getExpireMs(), remaining);
                if (refreshed != null) response.setHeader("X-Auth-Token", refreshed);
            }
        } catch (Exception e) {
            // 无效JWT或会话存储不可用时不赋予身份；公开页面仍可匿名浏览。
            UserContext.clear();
        }
        return true;
    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
