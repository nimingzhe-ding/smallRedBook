package com.hmdp.dto;

import lombok.Data;

import java.util.Map;

/**
 * 流程型 AI 请求。
 * 页面只传业务上下文，后端负责整理成不同场景的提示词。
 */
@Data
public class AiFlowRequest {
    private String sessionId;
    private String query;
    private Long noteId;
    private Long productId;
    private Long orderId;
    private Long merchantId;
    private Long voucherId;
    private String title;
    private String content;
    private String scenario;
    private Map<String, Object> payload;
    private Boolean reset;
}
