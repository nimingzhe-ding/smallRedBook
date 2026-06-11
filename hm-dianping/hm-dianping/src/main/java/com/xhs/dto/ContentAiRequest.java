package com.xhs.dto;

import lombok.Data;

/**
 * 内容社区 AI 请求 DTO。
 * 前端只传业务语义字段，由后端统一拼接提示词，避免页面里散落 AI prompt。
 */
@Data
public class ContentAiRequest {
    private String sessionId;
    private String query;
    private Long noteId;
    private String title;
    private String content;
    private Boolean reset;
}
