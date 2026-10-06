package com.geocommunity.common.auth;

import com.geocommunity.common.constant.RedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;


/**
 * 会话管理：维护"用户 → 活跃 token"索引（session:user:{userId}），
 * 使管理员封禁用户时能定位并删除其全部会话，实现强制下线。
 */
@Component
public class SessionService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public boolean isActive(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.SESSION_IDLE + token));
    }

    public void save(Long userId, String token, long ttlMs) {
        redisTemplate.execute(new DefaultRedisScript<>(
                "redis.call('SET',KEYS[1],'1','PX',ARGV[2]); redis.call('SADD',KEYS[2],ARGV[1]); "
                + "redis.call('PEXPIRE',KEYS[2],ARGV[2]); return 1", Long.class),
                List.of(RedisKeys.SESSION_IDLE + token, RedisKeys.SESSION_USER + userId), token, String.valueOf(ttlMs));
    }

    public String rotate(String oldToken, Long userId, String newToken, long ttlMs, long remainingMs) {
        String script = "if redis.call('EXISTS',KEYS[1]) == 0 then return nil end; "
                + "local existing=redis.call('GET',KEYS[3]); if existing then return existing end; "
                + "redis.call('SET',KEYS[4],'1','PX',ARGV[3]); redis.call('SADD',KEYS[2],ARGV[2]); "
                + "redis.call('PEXPIRE',KEYS[2],ARGV[3]); redis.call('PEXPIRE',KEYS[1],ARGV[4]); "
                + "redis.call('SET',KEYS[3],ARGV[2],'PX',ARGV[4]); "
                + "redis.call('SET',KEYS[5],ARGV[1],'PX',ARGV[4]); return ARGV[2]";
        return redisTemplate.execute(new DefaultRedisScript<>(script, String.class),
                List.of(RedisKeys.SESSION_IDLE + oldToken, RedisKeys.SESSION_USER + userId,
                        "session:rotation:" + oldToken, RedisKeys.SESSION_IDLE + newToken,
                        "session:parent:" + newToken), oldToken, newToken, String.valueOf(ttlMs),
                String.valueOf(Math.max(1, Math.min(60000, remainingMs))));
    }

    /** 撤销当前token及同一轮换产生的新旧token，其他设备会话不受影响。 */
    public void logout(Long userId, String token) {
        String script = "local parent=redis.call('GET',KEYS[3]); "
                + "local old=parent or ARGV[1]; local child=redis.call('GET',ARGV[3]..old); "
                + "redis.call('DEL',ARGV[2]..old,ARGV[3]..old,ARGV[4]..old); redis.call('SREM',KEYS[2],old); "
                + "redis.call('DEL',KEYS[1],KEYS[3]); redis.call('SREM',KEYS[2],ARGV[1]); "
                + "if child then redis.call('DEL',ARGV[2]..child,ARGV[4]..child); redis.call('SREM',KEYS[2],child); end; return 1";
        redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                List.of(RedisKeys.SESSION_IDLE + token, RedisKeys.SESSION_USER + userId, "session:parent:" + token),
                token, RedisKeys.SESSION_IDLE, "session:rotation:", "session:parent:");
    }

    /**
     * 强制下线：删除该用户所有活跃会话，其 JWT 随即失效，
     * 下次请求（含 /auth/check）即 401 → 前端自动登出。
     */
    public void forceLogout(Long userId) {
        if (userId == null) {
            return;
        }
        String sessionUserKey = RedisKeys.SESSION_USER + userId;
        redisTemplate.execute(new DefaultRedisScript<>(
                "local tokens=redis.call('SMEMBERS',KEYS[1]); for _,t in ipairs(tokens) do "
                + "redis.call('DEL',ARGV[1]..t,ARGV[2]..t,ARGV[3]..t); end; redis.call('DEL',KEYS[1]); return 1", Long.class),
                List.of(sessionUserKey), RedisKeys.SESSION_IDLE, "session:rotation:", "session:parent:");
    }
}
