package com.hmdp.privatemessage;

import com.hmdp.dto.PrivateMessageDTO;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PrivateMessageWebSocketEvent {

    private String type;
    private String requestId;
    private PrivateMessageDTO message;
}
