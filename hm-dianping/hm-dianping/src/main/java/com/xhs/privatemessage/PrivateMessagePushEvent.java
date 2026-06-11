package com.xhs.privatemessage;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class PrivateMessagePushEvent {

    private String type = "private_message_push";
    private String eventKey;
    private Long messageId;
    private Long conversationId;
    private Long senderId;
    private Long receiverId;
    private String requestId;
    private LocalDateTime createTime;
}
