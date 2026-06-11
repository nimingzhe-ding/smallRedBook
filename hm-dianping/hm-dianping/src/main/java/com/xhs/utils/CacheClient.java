package com.xhs.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    public CacheClient(StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogic(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }

        R value = dbFallback.apply(id);
        if (value == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        set(key, value, time, timeUnit);
        return value;
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Long time, TimeUnit timeUnit, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R value = JSONUtil.toBean(JSONUtil.parseObj(redisData.getData()), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            return value;
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return value;
        }
        if (!locked) {
            return value;
        }

        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                R freshValue = dbFallback.apply(id);
                setWithLogic(key, freshValue, time, timeUnit);
            } catch (Exception e) {
                log.error("重建缓存失败, key={}", key, e);
            } finally {
                try { lock.unlock(); } catch (Exception ignored) { /* 固定租约到期自动释放 */ }
            }
        });
        return value;
    }

    @PreDestroy
    public void shutdown() {
        CACHE_REBUILD_EXECUTOR.shutdown();
    }
}
