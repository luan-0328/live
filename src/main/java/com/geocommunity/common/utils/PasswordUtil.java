package com.geocommunity.common.utils;

import cn.hutool.crypto.digest.BCrypt;

/**
 * 密码哈希工具（BCrypt）。
 * 数据库中只存哈希，不存明文；登录时用 checkpw 校验。
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hash(String plain) {
        return BCrypt.hashpw(plain);
    }

    public static boolean matches(String plain, String hashed) {
        if (hashed == null || !hashed.startsWith("$2")) {
            return false;
        }
        return BCrypt.checkpw(plain, hashed);
    }

    /** 判断一个已存储的密码是否已是 BCrypt 哈希（用于旧明文数据迁移判断） */
    public static boolean isBcrypt(String stored) {
        return stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }
}
