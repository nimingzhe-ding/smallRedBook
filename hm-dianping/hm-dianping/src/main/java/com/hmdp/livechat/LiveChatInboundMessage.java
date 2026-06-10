package com.hmdp.livechat;

import lombok.Data;

@Data
public class LiveChatInboundMessage {

    private String requestId;
    private String type;
    private String content;
}
