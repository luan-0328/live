package com.geocommunity.task;

import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.entity.FailedNotification;
import com.geocommunity.mapper.FailedNotificationMapper;
import com.geocommunity.mq.MqEvent;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
public class FailedNotificationRetryTask {
    private final FailedNotificationMapper mapper;
    private final JsonUtil json;
    private final RabbitTemplate rabbit;
    public FailedNotificationRetryTask(FailedNotificationMapper mapper, JsonUtil json, RabbitTemplate rabbit) {
        this.mapper=mapper; this.json=json; this.rabbit=rabbit;
    }
    @Scheduled(fixedDelayString="${notification.retry-delay-ms:30000}")
    @Transactional(rollbackFor=Exception.class)
    public void retry() {
        for (FailedNotification row:mapper.lockDue()) {
            MqEvent event=json.fromJson(row.getPayload(),MqEvent.class);
            if(event==null || !row.getEventId().equals(event.getEventId()) || row.getRetryCount()>=3) {
                row.setStatus("EXHAUSTED"); row.setLastError("无效事件或达到重试上限"); mapper.updateById(row); continue;
            }
            int attempts=row.getRetryCount()+1;
            row.setRetryCount(attempts);
            try {
                CorrelationData confirmation=new CorrelationData(row.getEventId());
                rabbit.convertAndSend("geo.exchange","notification",event,confirmation);
                if(!confirmation.getFuture().get(5,TimeUnit.SECONDS).isAck() || confirmation.getReturned()!=null)
                    throw new IllegalStateException("发布未确认或未路由");
                row.setStatus("WAITING"); // 发布确认不是业务处理成功，等待消费结果。
                row.setLastError("已重发，等待消费结果");
            } catch(Exception error) {
                row.setStatus(attempts>=3?"EXHAUSTED":"PENDING");
                row.setLastError(error.getClass().getSimpleName());
                if(error instanceof InterruptedException) Thread.currentThread().interrupt();
            }
            row.setNextRetryAt(LocalDateTime.now().plusSeconds(attempts==1?300:1800));
            mapper.updateById(row);
            if(Thread.currentThread().isInterrupted()) break;
        }
    }
}
