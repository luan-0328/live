package com.geocommunity.common.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 读请求始终回填其读取时的版本，失效后的旧回填不会被新请求命中。 */
public final class CacheVersion {
    private CacheVersion() {}
    public static String key(StringRedisTemplate redis, String base, String namespace) {
        String version = redis.opsForValue().get(namespace + ":version");
        return version == null ? base : base + ":v:" + version;
    }
    public static void invalidate(StringRedisTemplate redis, String base, String namespace) {
        // UUID 防止版本键过期/Redis恢复后出现 ABA；版本寿命大于全部缓存 TTL。
        redis.opsForValue().set(namespace + ":version", UUID.randomUUID().toString(), 2, TimeUnit.HOURS);
        redis.delete(base);
    }
}
