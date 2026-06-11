package com.xhs.config;

import cn.hutool.core.util.StrUtil;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        String address = String.format("redis://%s:%s", redisProperties.getHost(), redisProperties.getPort());
        config.useSingleServer().setAddress(address);
        if (StrUtil.isNotBlank(redisProperties.getPassword())) {
            config.useSingleServer().setPassword(redisProperties.getPassword());
        }
        return Redisson.create(config);
    }
}
