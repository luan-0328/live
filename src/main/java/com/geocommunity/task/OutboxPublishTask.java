package com.geocommunity.task;

import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.entity.OutboxEvent;
import com.geocommunity.mapper.OutboxMapper;
import com.geocommunity.mq.MqEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublishTask {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublishTask.class);
    private final OutboxMapper mapper;
    private final JsonUtil json;
    private final RabbitTemplate rabbit;
    public OutboxPublishTask(OutboxMapper mapper, JsonUtil json, RabbitTemplate rabbit) {
        this.mapper = mapper; this.json = json; this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${notification.outbox-delay-ms:5000}")
    @Transactional(rollbackFor = Exception.class)
    public void publish() {
        // MySQL行锁及SKIP LOCKED支持多实例。确认后删除，崩溃重投由eventId去重。
        for (OutboxEvent row : mapper.lockPending()) {
            try {
                MqEvent event = json.fromJson(row.getPayload(), MqEvent.class);
                if (event == null) { log.error("Outbox事件格式错误: {}", row.getId()); continue; }
                CorrelationData correlation = new CorrelationData(row.getId());
                rabbit.convertAndSend("geo.exchange", "notification", event, correlation);
                if (!correlation.getFuture().get(5, TimeUnit.SECONDS).isAck() || correlation.getReturned() != null) {
                    log.warn("Outbox投递未确认或未路由，将重试: {}", row.getId());
                    break;
                }
                mapper.deleteById(row.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); break;
            } catch (Exception e) {
                log.warn("Outbox投递失败，将重试: {}", row.getId(), e); break;
            }
        }
    }
}
