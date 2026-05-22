package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrivateConversationDTO {
    private Long id;
    private Long peerUserId;
    private String peerNickName;
    private String peerIcon;
    private PrivateMessageUserDTO peerUser;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private Integer unreadCount;
    private LocalDateTime updatedAt;
}
