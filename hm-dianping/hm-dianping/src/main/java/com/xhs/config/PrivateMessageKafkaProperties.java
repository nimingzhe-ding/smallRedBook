package com.xhs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xiaohongshu.private-message.kafka")
public class PrivateMessageKafkaProperties {

    private String topic = "private-message";
    private String dltTopic = "private-message.DLT";
    private String pushConsumerGroup = "private-message-push-consumer-group";
    private String dltConsumerGroup = "private-message-dlt-compensation-group";
    private int partitions = 3;
    private short replicas = 1;
    private int listenerConcurrency = 3;
    private int outboxBatchSize = 100;
    private long outboxFixedDelayMillis = 1000L;
    private int outboxMaxRetry = 12;
    private long outboxInitialBackoffSeconds = 5L;
    private long outboxMaxBackoffSeconds = 300L;
    private long outboxLockSeconds = 120L;
}
