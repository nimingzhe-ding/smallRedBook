package com.xhs.service.impl;

import com.xhs.dto.PrivateMessageDTO;
import com.xhs.entity.PrivateMessage;
import com.xhs.mapper.PrivateMessageMapper;
import com.xhs.privatemessage.PrivateMessageWebSocketPushService;
import com.xhs.service.PrivateMessagePushDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrivateMessagePushDispatchServiceImpl implements PrivateMessagePushDispatchService {

    private final PrivateMessageMapper privateMessageMapper;
    private final PrivateMessageWebSocketPushService messagePushService;

    @Override
    public void dispatch(Long messageId, String requestId) {
        if (messageId == null) {
            return;
        }
        PrivateMessage message = privateMessageMapper.selectById(messageId);
        if (message == null) {
            return;
        }
        PrivateMessageDTO senderView = toMessageDTO(message, message.getSenderId());
        PrivateMessageDTO receiverView = toMessageDTO(message, message.getReceiverId());
        messagePushService.pushMessage(requestId, senderView, receiverView);
    }

    private PrivateMessageDTO toMessageDTO(PrivateMessage message, Long userId) {
        PrivateMessageDTO dto = new PrivateMessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setSenderId(message.getSenderId());
        dto.setReceiverId(message.getReceiverId());
        dto.setContent(message.getContent());
        dto.setReadFlag(message.getReadFlag());
        dto.setIsMe(userId != null && userId.equals(message.getSenderId()));
        dto.setCreateTime(message.getCreateTime());
        return dto;
    }
}
