package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.PrivateConversationRequest;
import com.hmdp.dto.PrivateMessageRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.PrivateMessage;

public interface IPrivateMessageService extends IService<PrivateMessage> {
    Result conversations();

    Result openConversation(PrivateConversationRequest request);

    Result messages(Long conversationId, Long beforeId, Integer limit);

    Result sendMessage(Long conversationId, PrivateMessageRequest request);

    Result markRead(Long conversationId);

    Result unreadCount();

    Result searchUsers(String keyword);
}
