package com.xhs.ai.knowledge;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.ai.config.AiAssistantProperties;
import com.xhs.ai.enums.AiScene;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiKnowledgeService {

    private final ObjectMapper objectMapper;
    private final AiAssistantProperties properties;
    private final Map<AiScene, List<AiKnowledgeItem>> knowledgeStore = new EnumMap<>(AiScene.class);

    @PostConstruct
    public void init() {
        knowledgeStore.put(AiScene.CUSTOMER_SERVICE, load("ai/knowledge/customer-service-knowledge.json"));
        knowledgeStore.put(AiScene.QUERY, load("ai/knowledge/query-knowledge.json"));
    }

    public List<AiKnowledgeItem> search(AiScene scene, String question) {
        List<AiKnowledgeItem> items = knowledgeStore.getOrDefault(scene, Collections.emptyList());
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedQuestion = normalize(question);
        List<ScoredKnowledge> scored = new ArrayList<>(items.size());
        for (AiKnowledgeItem item : items) {
            int score = score(item, normalizedQuestion);
            if (score > 0) {
                scored.add(new ScoredKnowledge(item, score));
            }
        }
        if (scored.isEmpty()) {
            return items.stream()
                    .filter(item -> StrUtil.equalsIgnoreCase(item.getId(), "general"))
                    .findFirst()
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
        scored.sort(Comparator.comparingInt(ScoredKnowledge::score).reversed());
        return scored.stream()
                .limit(Math.max(1, properties.getKnowledgeTopK()))
                .map(ScoredKnowledge::item)
                .toList();
    }

    private int score(AiKnowledgeItem item, String question) {
        int score = 0;
        if (StrUtil.isBlank(question)) {
            return score;
        }
        if (contains(question, item.getTitle())) {
            score += 6;
        }
        if (item.getKeywords() != null) {
            for (String keyword : item.getKeywords()) {
                if (contains(question, keyword)) {
                    score += 4;
                }
            }
        }
        String[] tokens = question.split("\\s+");
        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }
            if (contains(normalize(item.getContent()), token)) {
                score += 1;
            }
        }
        return score;
    }

    private boolean contains(String source, String target) {
        return StrUtil.isNotBlank(target) && StrUtil.contains(source, normalize(target));
    }

    private String normalize(String text) {
        return StrUtil.blankToDefault(text, "")
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}]", " ")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private List<AiKnowledgeItem> load(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return objectMapper.readValue(content, new TypeReference<List<AiKnowledgeItem>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("加载 AI 知识库失败: " + path, e);
        }
    }

    private record ScoredKnowledge(AiKnowledgeItem item, int score) {
    }
}
