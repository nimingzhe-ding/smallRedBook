package com.hmdp.danmaku;

import lombok.Data;

@Data
public class DanmakuInboundMessage {

    private String requestId;
    private String content;
    private Integer videoSecond;
    private Integer lane;
}
