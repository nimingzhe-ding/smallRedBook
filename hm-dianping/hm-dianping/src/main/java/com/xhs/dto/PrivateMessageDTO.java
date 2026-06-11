package com.xhs.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrivateMessageDTO {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Boolean readFlag;
    private Boolean isMe;
    private LocalDateTime createTime;
}
