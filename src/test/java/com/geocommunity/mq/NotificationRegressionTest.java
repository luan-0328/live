package com.geocommunity.mq;

import com.geocommunity.entity.Notification;
import com.geocommunity.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationRegressionTest {
    @Test void separateCommentsHaveDifferentIdsAndRetriesKeepTheirOriginalId() {
        NotificationMapper mapper = mock(NotificationMapper.class); MqConsumer consumer = new MqConsumer();
        ReflectionTestUtils.setField(consumer, "notificationMapper", mapper);
        ReflectionTestUtils.setField(consumer, "failures", mock(com.geocommunity.mq.FailedNotificationService.class));
        MqEvent first = new MqEvent("COMMENT", 1L, 2L, 3L, "first");
        MqEvent second = new MqEvent("COMMENT", 1L, 2L, 3L, "second");
        consumer.handleNotification(first); consumer.handleNotification(second); consumer.handleNotification(first);
        ArgumentCaptor<Notification> notices = ArgumentCaptor.forClass(Notification.class);
        verify(mapper, times(3)).insert(notices.capture());
        var records = notices.getAllValues();
        assertNotEquals(records.get(0).getEventId(), records.get(1).getEventId());
        assertEquals(records.get(0).getEventId(), records.get(2).getEventId());
    }
    @Test void followWithoutPostStillCarriesStableId() {
        NotificationMapper mapper = mock(NotificationMapper.class); MqConsumer consumer = new MqConsumer();
        ReflectionTestUtils.setField(consumer, "notificationMapper", mapper);
        ReflectionTestUtils.setField(consumer, "failures", mock(com.geocommunity.mq.FailedNotificationService.class));
        MqEvent follow = new MqEvent("FOLLOW", null, 1L, 2L, null); consumer.handleNotification(follow);
        verify(mapper).insert(argThat((Notification n) -> follow.getEventId().equals(n.getEventId()) && n.getPostId() == null));
    }
    @Test void legacyMessageIsRejectedForExplicitMigration() {
        MqEvent event = new MqEvent(); event.setType("FOLLOW");
        assertThrows(IllegalArgumentException.class, () -> new MqConsumer().handleNotification(event));
    }
    @Test void businessBindingDoesNotMatchDeadLetterRoutingKey() {
        assertEquals("notification", new com.geocommunity.config.RabbitConfig().notificationBinding().getRoutingKey());
    }
}
