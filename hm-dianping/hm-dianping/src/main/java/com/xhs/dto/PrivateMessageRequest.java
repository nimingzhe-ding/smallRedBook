package com.xhs.dto;

import lombok.Data;

@Data
public class PrivateMessageRequest {
    private String requestId;
    private String content;
}
