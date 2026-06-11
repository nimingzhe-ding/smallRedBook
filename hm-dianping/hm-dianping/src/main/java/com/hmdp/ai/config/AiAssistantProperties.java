package com.hmdp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hmdp.ai")
public class AiAssistantProperties {
    private Boolean enabled = false;
    private String model = "qwen-plus";
    private Double customerServiceTemperature = 0.6D;
    private Double queryTemperature = 0.2D;
    private Integer maxTokens = 1500;
    private Integer maxMessages = 12;
    private Integer knowledgeTopK = 3;
}
