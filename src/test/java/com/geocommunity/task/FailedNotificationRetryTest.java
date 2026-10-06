package com.geocommunity.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geocommunity.common.utils.JsonUtil;
import com.geocommunity.entity.FailedNotification;
import com.geocommunity.mapper.FailedNotificationMapper;
import com.geocommunity.mq.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FailedNotificationRetryTest {
    private final JsonUtil json=new JsonUtil(new ObjectMapper());
    @Test void confirmedRetryKeepsEventIdAndWaitsForConsumption() {
        var mapper=mock(FailedNotificationMapper.class); var rabbit=mock(RabbitTemplate.class);
        MqEvent event=new MqEvent("LIKE",1L,2L,3L,null); var row=row(event,0);
        when(mapper.lockDue()).thenReturn(List.of(row));
        doAnswer(call->{ ((CorrelationData)call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true,null)); return null; })
            .when(rabbit).convertAndSend(eq("geo.exchange"),eq("notification"),any(Object.class),any(CorrelationData.class));
        new FailedNotificationRetryTask(mapper,json,rabbit).retry();
        assertEquals("WAITING",row.getStatus()); assertEquals(1,row.getRetryCount());
        verify(rabbit).convertAndSend(eq("geo.exchange"),eq("notification"),argThat((Object value)->
            event.getEventId().equals(((MqEvent)value).getEventId())),any(CorrelationData.class));
        verify(mapper).updateById(row);
    }
    @Test void thirdPublishFailureIsRetainedWithoutMoreAutomaticRetries() {
        var mapper=mock(FailedNotificationMapper.class); var rabbit=mock(RabbitTemplate.class);
        var row=row(new MqEvent("LIKE",1L,2L,3L,null),2);
        when(mapper.lockDue()).thenReturn(List.of(row));
        doThrow(new IllegalStateException("broker down")).when(rabbit)
            .convertAndSend(anyString(),anyString(),any(Object.class),any(CorrelationData.class));
        new FailedNotificationRetryTask(mapper,json,rabbit).retry();
        assertEquals("EXHAUSTED",row.getStatus()); assertEquals(3,row.getRetryCount());
        assertNotNull(row.getPayload()); verify(mapper).updateById(row);
    }
    @Test void invalidPayloadIsRetainedAndNeverPublished() {
        var mapper=mock(FailedNotificationMapper.class); var rabbit=mock(RabbitTemplate.class);
        var row=new FailedNotification(); row.setPayload("invalid json"); row.setRetryCount(0);
        when(mapper.lockDue()).thenReturn(List.of(row));
        new FailedNotificationRetryTask(mapper,json,rabbit).retry();
        assertEquals("EXHAUSTED",row.getStatus()); verifyNoInteractions(rabbit);
    }
    @Test void malformedDeadLetterIsPersistedAsQuarantinedRawPayload() {
        var mapper=mock(FailedNotificationMapper.class); var service=new FailedNotificationService(mapper,json);
        service.recordRaw("invalid json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verify(mapper).record(argThat(row->"EXHAUSTED".equals(row.getStatus()) && "invalid json".equals(row.getPayload())));
    }
    @Test void deadLetterStorageFailureIsPropagatedForRequeue() {
        var mapper=mock(FailedNotificationMapper.class); var service=new FailedNotificationService(mapper,json);
        doThrow(new IllegalStateException("DB down")).when(mapper).record(any());
        assertThrows(IllegalStateException.class,()->service.record(new MqEvent("LIKE",1L,2L,3L,null)));
    }
    private FailedNotification row(MqEvent event,int retries) {
        var row=new FailedNotification(); row.setEventId(event.getEventId());
        row.setPayload(json.toJson(event)); row.setRetryCount(retries); row.setStatus("PENDING"); return row;
    }
}
