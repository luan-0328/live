package com.geocommunity.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserStatusRequest {
    @NotNull @Min(0) @Max(1) private Integer status;
}
