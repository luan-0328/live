package com.geocommunity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp="[0-9]{6}",message="验证码须为6位数字")
    private String code;

    @NotBlank(message = "昵称不能为空")
    @Size(min=2,max=12,message="昵称长度须为2～12字")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @Size(min = PasswordRule.MIN_LENGTH, max = PasswordRule.MAX_LENGTH,
            message = PasswordRule.LENGTH_MESSAGE)
    @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.COMPOSITION_MESSAGE)
    private String password;
}
