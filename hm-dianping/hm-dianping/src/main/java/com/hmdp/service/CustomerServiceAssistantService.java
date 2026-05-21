package com.hmdp.service;

import com.hmdp.ai.dto.AiChatRequest;
import com.hmdp.dto.AiFlowRequest;
import com.hmdp.dto.Result;

/**
 * 智能客服服务。
 */
public interface CustomerServiceAssistantService {
    Result chat(AiChatRequest request);

    Result flow(AiFlowRequest request);
}
