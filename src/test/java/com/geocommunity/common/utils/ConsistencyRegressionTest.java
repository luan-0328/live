package com.geocommunity.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geocommunity.entity.User;
import com.geocommunity.dto.PostWriteRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsistencyRegressionTest {
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    private void begin() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
    @Test void effectsOnlyRunAfterSuccessfulCommit() {
        AtomicInteger changes = new AtomicInteger(); begin(); AfterCommit.run(changes::incrementAndGet);
        assertEquals(0, changes.get());
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCommit();
        assertEquals(1, changes.get());
    }
    @Test void rollbackDoesNotApplyRedisEffects() {
        AtomicInteger changes = new AtomicInteger(); begin(); AfterCommit.run(changes::incrementAndGet);
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertEquals(0, changes.get());
    }
    @Test void staleReaderCannotRefillTheCurrentCacheVersion() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class); ValueOperations<String,String> values = mock(ValueOperations.class);
        Map<String,String> store = new HashMap<>(); when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> store.get(i.getArgument(0)));
        doAnswer(i -> { store.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(values).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        String staleKey = CacheVersion.key(redis, "post:detail:1", "post:detail:1");
        CacheVersion.invalidate(redis, "post:detail:1", "post:detail:1");
        store.put(staleKey, "old-post"); // 模拟提交前读到旧DB、提交后回填。
        String freshKey = CacheVersion.key(redis, "post:detail:1", "post:detail:1");
        assertNotEquals(staleKey, freshKey); assertNull(store.get(freshKey));
    }
    @Test void passwordHashNeverAppearsInUserJson() throws Exception {
        User user = new User(); user.setPassword("hash"); user.setNickname("test");
        assertFalse(new ObjectMapper().writeValueAsString(user).contains("password"));
    }
    @Test void postWriteDtoIgnoresProtectedFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var dto = mapper.readValue("{\"title\":\"t\",\"content\":\"c\",\"categoryId\":1,\"authorId\":99,\"likeCount\":999}", PostWriteRequest.class);
        assertNull(dto.toPost().getAuthorId()); assertNull(dto.toPost().getLikeCount());
    }
    @Test void invalidPostInputIsRejectedAtBackend() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            PostWriteRequest dto = new PostWriteRequest(); dto.setTitle(" "); dto.setContent(" "); dto.setLongitude(181.0);
            assertFalse(factory.getValidator().validate(dto).isEmpty());
        }
    }
    @Test void productionRejectsPublicDefaults() {
        var validator = new com.geocommunity.config.ProductionConfigValidator();
        org.springframework.test.util.ReflectionTestUtils.setField(validator, "jwtSecret", "dev-only-jwt-secret-change-me-please-32bytes+");
        org.springframework.test.util.ReflectionTestUtils.setField(validator, "adminPassword", "456281");
        assertThrows(IllegalStateException.class, validator::validate);
    }
    @Test void productionAcceptsExplicitLongCredentials() {
        var validator = new com.geocommunity.config.ProductionConfigValidator();
        org.springframework.test.util.ReflectionTestUtils.setField(validator, "jwtSecret", "c9088e65579b4bb5832f97ef02d62227b7b5fefb98704717");
        org.springframework.test.util.ReflectionTestUtils.setField(validator, "adminPassword", "AuditTestStrong#483");
        assertDoesNotThrow(validator::validate);
    }
}
