package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.PrivateConversationRequest;
import com.xhs.dto.PrivateMessageDTO;
import com.xhs.dto.PrivateMessageRequest;
import com.xhs.dto.Result;
import com.xhs.entity.PrivateMessage;

public interface IPrivateMessageService extends IService<PrivateMessage> {
    Result conversations();

    Result openConversation(PrivateConversationRequest request);

    Result messages(Long conversationId, Long beforeId, Integer limit);

    Result sendMessage(Long conversationId, PrivateMessageRequest request);

    PrivateMessageDTO sendMessageFromUser(Long conversationId, Long senderId, PrivateMessageRequest request);

    Result markRead(Long conversationId);

    Result unreadCount();

    Result searchUsers(String keyword);
}
