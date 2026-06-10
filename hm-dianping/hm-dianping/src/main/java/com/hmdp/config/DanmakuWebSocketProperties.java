package com.hmdp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hmdp.danmaku.websocket")
public class DanmakuWebSocketProperties {

    private boolean enabled = true;
    private int port = 8090;
    private String path = "/ws/danmaku";
    private int readerIdleSeconds = 75;
    private int maxFramePayloadLength = 1024;
}
