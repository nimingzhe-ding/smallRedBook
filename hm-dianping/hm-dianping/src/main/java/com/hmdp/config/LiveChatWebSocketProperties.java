package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.live-chat.websocket")
public class LiveChatWebSocketProperties {

    private boolean enabled = true;
    private int port = 8091;
    private String path = "/ws/live-chat";
    private int readerIdleSeconds = 75;
    private int maxFramePayloadLength = 1024;
    private int persistenceThreads = 2;
}
