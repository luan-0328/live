package com.geocommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geocommunity.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM category WHERE id=#{id} FOR UPDATE")
    Category selectForUpdate(@org.apache.ibatis.annotations.Param("id") Integer id);
}
