package com.geocommunity.common.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的计数器限流工具。
 * <p>
 * 限流状态必须放 Redis 而不是 JVM 内存：重启不丢、多实例共享，且靠 TTL 自动过期。
 * 本地 Map 既要手动清理又会随 key 数量无限增长，也无法表达「N 分钟后自动解锁」。
 * <p>
 * key 由调用方用 {@link com.geocommunity.common.constant.RedisKeys} 拼好传入，
 * 本类只负责计数语义，不关心业务维度（手机号 / IP / 账号）。
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 冷却式限流：window 内只放行一次。
     * 用 SET NX EX 一次性原子完成「判断 + 占位」，并发下不会两个请求同时通过
     * （先 GET 再 SET 的写法有竞态）。
     *
     * @return true 表示本次放行，false 表示还在冷却中
     */
    public boolean tryAcquire(String key, long window, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", window, unit));
    }

    /**
     * 固定窗口计数：计数 +1，超过 max 返回 true。首次计数时设定过期时间。
     * <p>
     * 窗口从第一次计数起算、不因后续请求延长——用于「每天最多 N 次」这类配额，
     * 否则持续请求会把窗口一直推后，配额永远不重置。
     */
    public boolean overLimit(String key, long max, long window, TimeUnit unit) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window, unit);
        }
        return count != null && count > max;
    }

    /**
     * 滑动窗口计数：计数 +1，并重置过期时间。
     * <p>
     * 用于失败次数这类「最后一次失败后必须等满窗口」的场景。
     * 若只按固定窗口，攻击者可卡在窗口末尾试探几次，实际等待时间远小于预期。
     */
    public void recordSliding(String key, long window, TimeUnit unit) {
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, window, unit);
    }

    /** 当前计数，key 不存在或已过期返回 0 */
    public int count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }

    /** 剩余过期秒数；key 不存在或无 TTL 返回 0 */
    public long remainingSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    /** 清除计数（如登录成功、验证码校验通过后重置失败次数） */
    public void clear(String key) {
        redisTemplate.delete(key);
    }
}
