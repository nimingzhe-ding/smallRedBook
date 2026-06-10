package com.hmdp.privatemessage;

import lombok.Data;

@Data
public class PrivateMessageInboundMessage {

    private String requestId;
    private Long conversationId;
    private String content;
}
