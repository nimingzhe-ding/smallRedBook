package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.private-message.websocket")
public class PrivateMessageWebSocketProperties {

    private boolean enabled = true;
    private int port = 8091;
    private String path = "/ws/messages";
    private int readerIdleSeconds = 75;
    private int maxFramePayloadLength = 2048;
}
