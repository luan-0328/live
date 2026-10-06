package com.geocommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geocommunity.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user WHERE id=#{id} FOR UPDATE")
    User selectForUpdate(@Param("id") Long id);
}
