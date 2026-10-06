package com.geocommunity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geocommunity.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface OutboxMapper extends BaseMapper<OutboxEvent> {
    @Select("SELECT id,payload,created_at FROM notification_outbox ORDER BY created_at,id LIMIT 20 FOR UPDATE SKIP LOCKED")
    List<OutboxEvent> lockPending();
}
