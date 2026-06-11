package com.hmdp.ai.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.hmdp.ai.config.AiAssistantProperties;
import com.hmdp.ai.dto.AiChatRequest;
import com.hmdp.ai.dto.AiChatResponse;
import com.hmdp.ai.enums.AiScene;
import com.hmdp.ai.knowledge.AiKnowledgeItem;
import com.hmdp.ai.knowledge.AiKnowledgeService;
import com.hmdp.ai.prompt.AiPromptService;
import com.hmdp.ai.tool.HmDianPingAiTools;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiAssistantService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ChatMemory aiChatMemory;
    private final AiAssistantProperties properties;
    private final AiKnowledgeService knowledgeService;
    private final AiPromptService promptService;
    private final HmDianPingAiTools aiTools;

    private final Map<AiScene, ChatClient> clientCache = new EnumMap<>(AiScene.class);

    public AiChatResponse customerService(AiChatRequest request) {
        return chat(AiScene.CUSTOMER_SERVICE, request);
    }

    public AiChatResponse query(AiChatRequest request) {
        return chat(AiScene.QUERY, request);
    }

    public AiChatResponse flow(AiChatRequest request) {
        return chat(AiScene.FLOW, request);
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled());
    }

    public void clearConversation(AiScene scene, String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        aiChatMemory.clear(scene.conversationId(sessionId.trim()));
    }

    private AiChatResponse chat(AiScene scene, AiChatRequest request) {
        if (request == null || StrUtil.isBlank(request.getMessage())) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (!isEnabled()) {
            throw new IllegalStateException("AI assistant is disabled");
        }
        String sessionId = StrUtil.blankToDefault(StrUtil.trim(request.getSessionId()), IdUtil.fastSimpleUUID());
        String conversationId = scene.conversationId(sessionId);
        if (Boolean.TRUE.equals(request.getReset())) {
            aiChatMemory.clear(conversationId);
        }
        UserDTO currentUser = UserHolder.getUser();
        Long currentUserId = currentUser == null ? null : currentUser.getId();
        List<AiKnowledgeItem> knowledgeItems = knowledgeService.search(scene, request.getMessage());
        String answer;
        try {
            answer = getClient(scene).prompt()
                    .advisors(MessageChatMemoryAdvisor.builder(aiChatMemory)
                            .conversationId(conversationId)
                            .build())
                    .user(buildUserMessage(scene, sessionId, currentUserId, request.getMessage(), knowledgeItems))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 对话失败, scene={}, sessionId={}", scene.getCode(), sessionId, e);
            throw new IllegalStateException("AI 服务调用失败，请检查 DashScope API Key、模型配置和网络连接。");
        }
        return new AiChatResponse(
                scene.getCode(),
                sessionId,
                StrUtil.blankToDefault(answer, "未获得有效回复，请稍后重试。"),
                knowledgeItems.stream().map(AiKnowledgeItem::getTitle).toList(),
                currentUserId,
                LocalDateTime.now()
        );
    }

    private ChatClient getClient(AiScene scene) {
        ChatClient cachedClient = clientCache.get(scene);
        if (cachedClient != null) {
            return cachedClient;
        }
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new IllegalStateException("未检测到 Spring AI Alibaba ChatClient.Builder，请确认 DashScope Starter 已正确加载。");
        }
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(properties.getModel())
                .withTemperature(scene == AiScene.CUSTOMER_SERVICE
                        ? properties.getCustomerServiceTemperature()
                        : properties.getQueryTemperature())
                .withMaxToken(properties.getMaxTokens())
                .withParallelToolCalls(Boolean.FALSE)
                .withInternalToolExecutionEnabled(Boolean.TRUE)
                .build();
        ChatClient client = builder.clone()
                .defaultSystem(promptService.getPrompt(scene))
                .defaultTools(aiTools)
                .defaultOptions(options)
                .build();
        clientCache.put(scene, client);
        return client;
    }

    private String buildUserMessage(AiScene scene, String sessionId, Long currentUserId, String question,
                                    List<AiKnowledgeItem> knowledgeItems) {
        StringBuilder prompt = new StringBuilder(512);
        prompt.append("当前场景：").append(scene.getDescription()).append('\n');
        prompt.append("当前时间：").append(DATE_TIME_FORMATTER.format(LocalDateTime.now())).append('\n');
        prompt.append("当前会话ID：").append(sessionId).append('\n');
        prompt.append("当前登录用户ID：").append(currentUserId == null ? "未登录" : currentUserId).append('\n');
        if (!knowledgeItems.isEmpty()) {
            prompt.append("命中的业务知识：\n");
            for (AiKnowledgeItem knowledgeItem : knowledgeItems) {
                prompt.append("[").append(knowledgeItem.getTitle()).append("]\n");
                prompt.append(knowledgeItem.getContent()).append('\n');
            }
        }
        prompt.append("用户问题：").append(question).append('\n');
        prompt.append("请结合系统规则、历史对话、命中的业务知识和工具查询结果来回答。");
        return prompt.toString();
    }
}
