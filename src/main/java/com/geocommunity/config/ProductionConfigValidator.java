package com.geocommunity.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 生产环境拒绝公开的开发密钥和管理员默认密码。 */
@Component
@Profile("prod")
public class ProductionConfigValidator {
    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${admin.init-password:456281}") private String adminPassword;
    @PostConstruct
    public void validate() {
        if (jwtSecret.length() < 32 || jwtSecret.toLowerCase().contains("change") || jwtSecret.startsWith("dev-only")) {
            throw new IllegalStateException("生产环境必须设置随机JWT_SECRET，至少32字符");
        }
        if (adminPassword.length() < 12 || adminPassword.equals("456281") || adminPassword.toLowerCase().contains("please-change")) {
            throw new IllegalStateException("生产环境必须设置至少12字符的ADMIN_INIT_PASSWORD");
        }
    }
}
