package com.xhs.ai.enums;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;

public enum AiScene {
    CUSTOMER_SERVICE("customer-service", "智能客服"),
    QUERY("query", "智能查询问答"),
    FLOW("flow", "流程型智能助手");

    private final String code;
    private final String description;

    AiScene(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String conversationId(String sessionId) {
        return code + ":" + sessionId;
    }

    public static AiScene fromCode(String code) {
        return Arrays.stream(values())
                .filter(scene -> StrUtil.equalsIgnoreCase(scene.code, code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的 AI 场景：" + code));
    }
}
