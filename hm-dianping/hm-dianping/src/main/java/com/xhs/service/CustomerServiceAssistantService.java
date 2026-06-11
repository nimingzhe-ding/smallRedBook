package com.xhs.service;

import com.xhs.ai.dto.AiChatRequest;
import com.xhs.dto.AiFlowRequest;
import com.xhs.dto.Result;

/**
 * 智能客服服务。
 */
public interface CustomerServiceAssistantService {
    Result chat(AiChatRequest request);

    Result flow(AiFlowRequest request);
}
