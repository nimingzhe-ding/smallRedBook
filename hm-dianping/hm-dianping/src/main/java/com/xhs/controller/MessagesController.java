package com.xhs.controller;

import com.xhs.annotation.Idempotent;
import com.xhs.annotation.SlidingWindowRateLimit;
import com.xhs.dto.PrivateConversationRequest;
import com.xhs.dto.PrivateMessageRequest;
import com.xhs.dto.Result;
import com.xhs.enums.RateLimitScope;
import com.xhs.service.IPrivateMessageService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
public class MessagesController {
    @Resource
    private IPrivateMessageService privateMessageService;

    @GetMapping("/conversations")
    @SlidingWindowRateLimit(key = "messages:conversations", maxRequests = 120, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result conversations() {
        return privateMessageService.conversations();
    }

    @PostMapping("/conversations")
    @SlidingWindowRateLimit(key = "messages:open-conversation", maxRequests = 30, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    @Idempotent(key = "messages:open-conversation", expireSeconds = 10)
    public Result openConversation(@RequestBody PrivateConversationRequest request) {
        return privateMessageService.openConversation(request);
    }

    @GetMapping("/conversations/{id}/messages")
    @SlidingWindowRateLimit(key = "messages:list", maxRequests = 180, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result messages(@PathVariable("id") Long conversationId,
                           @RequestParam(value = "beforeId", required = false) Long beforeId,
                           @RequestParam(value = "limit", defaultValue = "30") Integer limit) {
        return privateMessageService.messages(conversationId, beforeId, limit);
    }

    @PostMapping("/conversations/{id}/messages")
    @SlidingWindowRateLimit(key = "messages:send", maxRequests = 120, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    @Idempotent(key = "messages:send", expireSeconds = 5)
    public Result sendMessage(@PathVariable("id") Long conversationId,
                              @RequestBody PrivateMessageRequest request) {
        return privateMessageService.sendMessage(conversationId, request);
    }

    @PostMapping("/conversations/{id}/read")
    @Idempotent(key = "messages:read", expireSeconds = 5)
    public Result markRead(@PathVariable("id") Long conversationId) {
        return privateMessageService.markRead(conversationId);
    }

    @GetMapping("/unread-count")
    public Result unreadCount() {
        return privateMessageService.unreadCount();
    }

    @GetMapping("/users")
    @SlidingWindowRateLimit(key = "messages:users", maxRequests = 60, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result searchUsers(@RequestParam(value = "keyword", required = false) String keyword) {
        return privateMessageService.searchUsers(keyword);
    }
}
