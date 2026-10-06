package com.geocommunity.mq;

import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.entity.OutboxEvent;
import com.geocommunity.mapper.OutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/** 与业务写入共用数据库事务，不在事务提交前投递通知。 */
@Service
public class OutboxService {
    private final OutboxMapper mapper;
    private final JsonUtil json;
    public OutboxService(OutboxMapper mapper, JsonUtil json) { this.mapper = mapper; this.json = json; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(MqEvent event) {
        OutboxEvent row = new OutboxEvent();
        row.setId(event.getEventId()); row.setPayload(json.toJson(event)); row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
    }
}
