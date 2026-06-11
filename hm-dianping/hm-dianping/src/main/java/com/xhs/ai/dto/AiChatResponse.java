package com.xhs.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String scene;
    private String sessionId;
    private String answer;
    private List<String> knowledgeRefs;
    private Long currentUserId;
    private LocalDateTime timestamp;
}
