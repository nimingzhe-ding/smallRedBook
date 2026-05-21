package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiCustomerServiceResponse {
    private String scene;
    private String sessionId;
    private String answer;
    private String source;
    private String scenario;
    private Boolean requiresLogin;
    private List<String> knowledgeRefs;
    private Long currentUserId;
    private LocalDateTime timestamp;
    private ContextSummary context;
    private List<Action> actions;

    @Data
    public static class ContextSummary {
        private Boolean loggedIn;
        private String pageScenario;
        private Boolean orderRequested;
        private Boolean orderDenied;
        private Long orderId;
        private String orderStatus;
        private Long productId;
        private String productTitle;
        private Integer productStock;
        private Long voucherId;
        private String voucherTitle;
    }

    @Data
    public static class Action {
        private String code;
        private String label;
        private Long orderId;
        private Long productId;
        private Long voucherId;
    }
}
