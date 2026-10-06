package com.geocommunity.task;

import com.geocommunity.common.constant.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/** 原子衰减当前分数，同一30分钟时间窗跨实例只执行一次。 */
@Component
public class HotBoardDecayTask {
    private static final Logger log = LoggerFactory.getLogger(HotBoardDecayTask.class);
    @Autowired private StringRedisTemplate redisTemplate;
    static final DefaultRedisScript<Long> DECAY = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[2]) == ARGV[1] then return 0 end; "
            + "if redis.call('EXISTS',KEYS[1]) == 1 then "
            + "redis.call('ZUNIONSTORE',KEYS[1],1,KEYS[1],'WEIGHTS',ARGV[2]); "
            + "redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf','(1'); end; "
            + "redis.call('SET',KEYS[2],ARGV[1],'EX',3600); return 1", Long.class);

    @Scheduled(fixedRate = 1800000L)
    public void decayHotBoard() {
        try {
            redisTemplate.execute(DECAY, List.of(RedisKeys.POST_HOT_BOARD, "post:hot:decay-window"),
                    String.valueOf(System.currentTimeMillis() / 1800000L), "0.8");
        } catch (Exception e) { log.warn("热榜原子衰减失败，下个周期重试", e); }
    }
}
