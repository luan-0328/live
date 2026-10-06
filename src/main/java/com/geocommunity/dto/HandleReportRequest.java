package com.geocommunity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HandleReportRequest {

    @NotNull(message = "处理结果不能为空")
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(2)
    private Integer status; // 1 违规成立  2 驳回

    @jakarta.validation.constraints.Size(max=500,message="备注最多500字")
    private String handleNote;
}
