package com.geocommunity.dto;

import lombok.Data;

@Data
public class UpdatePhoneRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="^1\\d{10}$",message="手机号格式不正确")
    private String newPhone;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="[0-9]{6}",message="验证码须为6位数字")
    private String code;
}
