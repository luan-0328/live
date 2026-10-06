package com.geocommunity.common.auth;

import com.geocommunity.common.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.io.IOException;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final Set<String> PUBLIC_GET = Set.of(
            "/post/list", "/post/search", "/post/nearby", "/category/list", "/rank/hot",
            "/actuator/health", "/actuator/info");
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod()) && (PUBLIC_GET.contains(path)
                || path.matches("/post/\\d+") || path.matches("/post/\\d+/comments")
                || path.matches("/user/\\d+/profile"))) return true;
        if (UserContext.get() != null) return true;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"请先登录或重新登录\",\"data\":null}");
        return false;
    }
}
