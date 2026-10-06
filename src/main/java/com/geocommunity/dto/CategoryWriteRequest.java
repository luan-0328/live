package com.geocommunity.dto;

import com.geocommunity.entity.Category;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CategoryWriteRequest {
    @NotBlank(message="分类名称不能为空") @Size(max=20,message="分类名称最多20字")
    private String name;
    @NotNull(message="排序不能为空") @Min(value=0,message="排序不能小于0")
    private Integer sort;
    public Category toCategory() {
        Category category=new Category(); category.setName(name.trim()); category.setSort(sort); return category;
    }
}
