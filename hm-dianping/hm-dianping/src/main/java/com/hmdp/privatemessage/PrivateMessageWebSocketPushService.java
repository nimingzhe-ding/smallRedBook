package com.hmdp.privatemessage;

import com.hmdp.dto.PrivateMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrivateMessageWebSocketPushService {

    private final PrivateMessageConnectionRegistry connectionRegistry;

    public void pushMessage(String requestId, PrivateMessageDTO senderView, PrivateMessageDTO receiverView) {
        if (senderView != null && senderView.getSenderId() != null) {
            connectionRegistry.push(senderView.getSenderId(), event(requestId, senderView));
        }
        if (receiverView != null && receiverView.getReceiverId() != null) {
            connectionRegistry.push(receiverView.getReceiverId(), event(requestId, receiverView));
        }
    }

    private PrivateMessageWebSocketEvent event(String requestId, PrivateMessageDTO message) {
        return new PrivateMessageWebSocketEvent()
                .setType("private_message")
                .setRequestId(requestId)
                .setMessage(message);
    }
}
