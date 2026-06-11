package com.xhs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xiaohongshu.seckill.kafka")
public class SeckillKafkaProperties {

    private String orderTopic = "seckill-order";
    private String orderDltTopic = "seckill-order.DLT";
    private String orderConsumerGroup = "seckill-order-consumer-group";
    private int partitions = 3;
    private short replicas = 1;
    private int listenerConcurrency = 3;
}
