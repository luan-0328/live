package com.geocommunity.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    @jakarta.validation.constraints.Size(min=2,max=12,message="昵称长度须为2～12字")
    @jakarta.validation.constraints.Pattern(regexp=".*\\S.*",message="昵称不能为空白")
    private String nickname;
    @jakarta.validation.constraints.Size(max=500,message="头像地址最多500字符")
    private String avatar;
    @jakarta.validation.constraints.AssertTrue(message="请至少提供昵称或头像")
    public boolean isUpdateProvided() { return nickname!=null || avatar!=null; }
}
