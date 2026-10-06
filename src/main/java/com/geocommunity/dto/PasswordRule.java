package com.geocommunity.dto;

/**
 * 密码强度规则，由注册和修改密码两处共用，避免规则散落或走样。
 * <p>
 * 限定「可打印 ASCII、不含空格」是有意为之：BCrypt 只取密码的前 72 字节，
 * 而 UTF-8 下一个中文字符占 3 字节，超过 24 个字就会被静默截断——
 * 用户会遇到「新密码设置成功，但输入完整新密码却登不上」这类极难排查的问题。
 * 把长度上限压在 64 位 ASCII 内，就从根上避开了截断。
 */
public final class PasswordRule {

    private PasswordRule() {
    }

    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 64;

    public static final String LENGTH_MESSAGE = "密码长度需为 8~64 位";

    /** 至少一个字母、一个数字，且只允许可打印 ASCII（不含空格） */
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$";

    public static final String COMPOSITION_MESSAGE = "密码需至少包含一个字母和一个数字，且只能使用字母、数字和常见符号";
}
