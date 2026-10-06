package com.geocommunity.config;

import com.geocommunity.common.auth.AuthInterceptor;
import com.geocommunity.common.auth.RefreshInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private RefreshInterceptor refreshInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 0：所有请求都过，解析 token、刷 idle、放 ThreadLocal，始终放行
        registry.addInterceptor(refreshInterceptor).order(0).addPathPatterns("/**");
        // 1：保护需要登录的接口，从 ThreadLocal 取 userId，没有就拦
        registry.addInterceptor(authInterceptor).order(1).addPathPatterns("/**")
                .excludePathPatterns("/auth/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Auth-Token")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
