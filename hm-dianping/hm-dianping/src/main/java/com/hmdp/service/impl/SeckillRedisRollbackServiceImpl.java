package com.hmdp.service.impl;

import com.hmdp.service.SeckillRedisRollbackService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class SeckillRedisRollbackServiceImpl implements SeckillRedisRollbackService {

    private static final DefaultRedisScript<Long> ROLLBACK_SECKILL_SCRIPT;

    static {
        ROLLBACK_SECKILL_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SECKILL_SCRIPT.setLocation(new ClassPathResource("rollback-seckill.lua"));
        ROLLBACK_SECKILL_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate stringRedisTemplate;

    public SeckillRedisRollbackServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void rollback(Long voucherId, Long userId) {
        if (voucherId == null || userId == null) {
            return;
        }
        stringRedisTemplate.execute(
                ROLLBACK_SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
    }
}
