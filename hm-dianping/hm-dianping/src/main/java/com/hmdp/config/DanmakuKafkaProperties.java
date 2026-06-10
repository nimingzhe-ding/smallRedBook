package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.danmaku.kafka")
public class DanmakuKafkaProperties {

    private String topic = "video-danmaku";
    private String dltTopic = "video-danmaku.DLT";
    private String consumerGroup = "video-danmaku-consumer-group";
    private int partitions = 3;
    private short replicas = 1;
    private int listenerConcurrency = 2;
    private long cacheLimit = 300L;
    private long cacheTtlHours = 48L;
}
