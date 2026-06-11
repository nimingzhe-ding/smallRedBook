package com.hmdp.config;

import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SlidingWindowRateLimiter {

    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
        SLIDING_WINDOW_SCRIPT.setScriptText("""
                redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])
                local count = redis.call('ZCARD', KEYS[1])
                local limit = tonumber(ARGV[3])
                if count >= limit then
                    redis.call('EXPIRE', KEYS[1], ARGV[4])
                    return count + 1
                end
                redis.call('ZADD', KEYS[1], ARGV[2], ARGV[5])
                redis.call('EXPIRE', KEYS[1], ARGV[4])
                return count + 1
                """);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public SlidingWindowRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long hit(String rawKey, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowStart = now - Math.max(1, windowSeconds) * 1000L;
        String key = RedisConstants.RATE_LIMIT_KEY + RequestKeySupport.sha256(rawKey);
        String member = now + ":" + UUID.randomUUID();
        Long count = stringRedisTemplate.execute(
                SLIDING_WINDOW_SCRIPT,
                List.of(key),
                String.valueOf(windowStart),
                String.valueOf(now),
                String.valueOf(Math.max(1, maxRequests)),
                String.valueOf(Math.max(1, windowSeconds) + 1),
                member
        );
        return count == null ? 1 : count;
    }
}
