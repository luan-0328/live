package com.geocommunity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePasswordRequest {
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = PasswordRule.MIN_LENGTH, max = PasswordRule.MAX_LENGTH,
            message = PasswordRule.LENGTH_MESSAGE)
    @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.COMPOSITION_MESSAGE)
    private String newPassword;
}
