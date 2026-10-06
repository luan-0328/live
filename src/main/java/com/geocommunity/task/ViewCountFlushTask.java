package com.geocommunity.task;

import com.geocommunity.common.constant.RedisKeys;
import com.geocommunity.common.utils.CacheVersion;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.geocommunity.entity.Post;
import com.geocommunity.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 定时将 Redis 中的浏览计数刷到 MySQL。
 * <p>
 * 浏览计数走 Redis INCR（见 PostServiceImpl.incrementViewCount），
 * 本任务每 5 分钟读取一次，批量 UPDATE 到 post.view_count，然后重置。
 * 即使任务宕机几次，最多丢 5 分钟的浏览数据，可以接受。
 */
@Component
public class ViewCountFlushTask {

    private static final Logger log = LoggerFactory.getLogger(ViewCountFlushTask.class);

    private static final String KEY_VIEW_COUNT = RedisKeys.POST_VIEW;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PostMapper postMapper;

    /**
     * 每 5 分钟执行一次
     */
    @Scheduled(fixedRate = 300_000)
    public void flushViewCounts() {
        // 用 SCAN 代替 KEYS，避免大数据量时阻塞 Redis
        Set<String> keys = scanKeys(KEY_VIEW_COUNT + "*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            try {
                String countStr = redisTemplate.opsForValue().getAndDelete(key);
                if (countStr == null) {
                    continue;
                }
                long count = Long.parseLong(countStr);
                if (count <= 0) {
                    continue;
                }

                Long postId = Long.parseLong(key.substring(KEY_VIEW_COUNT.length()));
                try {
                    int changed = postMapper.update(null, new LambdaUpdateWrapper<Post>()
                            .eq(Post::getId, postId)
                            .setSql("view_count = view_count + " + count));
                    if (changed == 1) {
                        // SQL已经提交，缓存失败不能补偿计数，否则会重复计入。
                        try {
                            CacheVersion.invalidate(redisTemplate, RedisKeys.POST_DETAIL + postId, RedisKeys.POST_DETAIL + postId);
                            CacheVersion.invalidate(redisTemplate, RedisKeys.POST_LIST, "post:lists");
                        } catch (Exception cacheError) { log.warn("浏览数缓存失效失败: {}", postId, cacheError); }
                    }
                } catch (Exception e) {
                    // DB 写失败：把计数加回 Redis，避免丢失，下次任务重试
                    redisTemplate.opsForValue().increment(key, count);
                    log.warn("浏览计数刷 DB 失败，已补偿回 Redis: key={}, count={}", key, count, e);
                }
            } catch (Exception e) {
                log.warn("浏览计数刷 DB 失败: key={}", key, e);
            }
        }
    }

    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> matched = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                    .match(pattern)
                    .count(200)
                    .build())) {
                while (cursor.hasNext()) {
                    matched.add(new String(cursor.next()));
                }
            } catch (Exception e) {
                log.warn("SCAN 浏览计数 key 失败: pattern={}", pattern, e);
            }
            return matched;
        });
    }
}
