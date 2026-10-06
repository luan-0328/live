package com.geocommunity.mq;

import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.entity.FailedNotification;
import com.geocommunity.mapper.FailedNotificationMapper;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class FailedNotificationService {
    private final FailedNotificationMapper mapper;
    private final JsonUtil json;
    public FailedNotificationService(FailedNotificationMapper mapper, JsonUtil json) {
        this.mapper=mapper; this.json=json;
    }
    public void record(MqEvent event) {
        String payload=json.toJson(event);
        boolean valid=event.getEventId()!=null && !event.getEventId().isBlank()
            && java.util.Set.of("LIKE","COMMENT","FOLLOW","NEW_POST").contains(event.getType()==null?"":event.getType());
        FailedNotification row=new FailedNotification();
        row.setEventId(event.getEventId()!=null && !event.getEventId().isBlank()?event.getEventId():
            UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8)).toString());
        row.setPayload(payload); row.setStatus(valid?"PENDING":"EXHAUSTED");
        row.setLastError(valid?"消费重试耗尽，进入死信":"事件ID或类型无效，需人工迁移");
        mapper.record(row); // 保存失败必须抛出，不能确认并丢弃原消息。
    }
    public void recordRaw(byte[] body) {
        String payload=new String(body,StandardCharsets.UTF_8);
        MqEvent event=json.fromJson(payload,MqEvent.class);
        if(event!=null) { record(event); return; }
        FailedNotification row=new FailedNotification();
        row.setEventId(UUID.nameUUIDFromBytes(body).toString()); row.setPayload(payload);
        row.setStatus("EXHAUSTED"); row.setLastError("无法解析的原始消息，需人工处理");
        mapper.record(row);
    }
    public void resolve(String eventId) { mapper.resolve(eventId); }
}
