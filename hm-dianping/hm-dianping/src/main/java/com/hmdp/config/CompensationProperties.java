package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.compensation")
public class CompensationProperties {

    private boolean enabled = true;
    private int batchSize = 20;
    private long fixedDelayMillis = 5000L;
    private int maxRetry = 12;
    private long initialBackoffSeconds = 30L;
    private long maxBackoffSeconds = 3600L;
    private long lockSeconds = 300L;
}
