package com.xhs.ai.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class AiKnowledgeItem {
    private String id;
    private String title;
    private List<String> keywords;
    private String content;
}
