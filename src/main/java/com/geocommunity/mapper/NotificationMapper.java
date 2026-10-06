package com.geocommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geocommunity.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 批量插入通知 */
    void insertBatch(@Param("list") List<Notification> list);
}
