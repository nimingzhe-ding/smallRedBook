package com.hmdp.service;

public interface PrivateMessagePushDispatchService {

    void dispatch(Long messageId, String requestId);
}
