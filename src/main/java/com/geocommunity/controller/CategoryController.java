package com.geocommunity.controller;

import com.geocommunity.common.result.Result;
import com.geocommunity.entity.Category;
import com.geocommunity.mapper.CategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;

@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryMapper categoryMapper;

    @GetMapping("/list")
    public Result<List<Category>> list() {
        List<Category> list = categoryMapper.selectList(null);
        return Result.ok(list);
    }
}
