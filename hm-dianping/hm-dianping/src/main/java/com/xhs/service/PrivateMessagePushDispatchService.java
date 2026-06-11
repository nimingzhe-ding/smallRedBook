package com.xhs.service;

public interface PrivateMessagePushDispatchService {

    void dispatch(Long messageId, String requestId);
}
