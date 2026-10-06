package com.geocommunity.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.entity.OutboxEvent;
import com.geocommunity.mapper.OutboxMapper;
import com.geocommunity.mq.MqEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.List;
import static org.mockito.Mockito.*;

class OutboxPublishTaskTest {
    private void publish(boolean acknowledged) {
        OutboxMapper mapper = mock(OutboxMapper.class); RabbitTemplate rabbit = mock(RabbitTemplate.class);
        JsonUtil json = new JsonUtil(new ObjectMapper());
        MqEvent event = new MqEvent("LIKE", 1L, 2L, 3L, null);
        OutboxEvent row = new OutboxEvent(); row.setId(event.getEventId()); row.setPayload(json.toJson(event));
        when(mapper.lockPending()).thenReturn(List.of(row));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, acknowledged ? null : "nack"));
            return null;
        }).when(rabbit).convertAndSend(eq("geo.exchange"), eq("notification"), any(MqEvent.class), any(CorrelationData.class));
        new OutboxPublishTask(mapper, json, rabbit).publish();
        verify(mapper, acknowledged ? times(1) : never()).deleteById(row.getId());
    }
    @Test void ackRemovesDeliveredRecord() { publish(true); }
    @Test void nackKeepsRecordForRetry() { publish(false); }
    @Test void brokerFailureKeepsRecordForRetry() {
        OutboxMapper mapper = mock(OutboxMapper.class); RabbitTemplate rabbit = mock(RabbitTemplate.class);
        JsonUtil json = new JsonUtil(new ObjectMapper());
        OutboxEvent row = new OutboxEvent(); row.setId("test"); row.setPayload(json.toJson(new MqEvent("LIKE", 1L, 2L, 3L, null)));
        when(mapper.lockPending()).thenReturn(List.of(row));
        doThrow(new IllegalStateException("offline")).when(rabbit).convertAndSend(anyString(), anyString(), any(MqEvent.class), any(CorrelationData.class));
        new OutboxPublishTask(mapper, json, rabbit).publish();
        verify(mapper, never()).deleteById(anyString());
    }
}
