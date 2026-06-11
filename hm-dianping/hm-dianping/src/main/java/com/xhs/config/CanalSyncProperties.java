package com.xhs.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xiaohongshu.canal")
public class CanalSyncProperties {

    private boolean enabled = false;
    private String host = "127.0.0.1";
    private int port = 11111;
    private String destination = "example";
    private String username = "";
    private String password = "";
    private String subscribe = ".*\\.tb_blog,.*\\.tb_user,.*\\.tb_video_danmaku";
    private int batchSize = 1000;
    private long idleSleepMillis = 500L;
    private long initialBackoffMillis = 1000L;
    private long maxBackoffMillis = 30000L;
    private long cacheTtlMinutes = 30L;
}
