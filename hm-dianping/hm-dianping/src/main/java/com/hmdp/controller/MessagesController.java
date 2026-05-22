package com.hmdp.controller;

import com.hmdp.dto.PrivateConversationRequest;
import com.hmdp.dto.PrivateMessageRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.IPrivateMessageService;
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
    public Result conversations() {
        return privateMessageService.conversations();
    }

    @PostMapping("/conversations")
    public Result openConversation(@RequestBody PrivateConversationRequest request) {
        return privateMessageService.openConversation(request);
    }

    @GetMapping("/conversations/{id}/messages")
    public Result messages(@PathVariable("id") Long conversationId,
                           @RequestParam(value = "beforeId", required = false) Long beforeId,
                           @RequestParam(value = "limit", defaultValue = "30") Integer limit) {
        return privateMessageService.messages(conversationId, beforeId, limit);
    }

    @PostMapping("/conversations/{id}/messages")
    public Result sendMessage(@PathVariable("id") Long conversationId,
                              @RequestBody PrivateMessageRequest request) {
        return privateMessageService.sendMessage(conversationId, request);
    }

    @PostMapping("/conversations/{id}/read")
    public Result markRead(@PathVariable("id") Long conversationId) {
        return privateMessageService.markRead(conversationId);
    }

    @GetMapping("/unread-count")
    public Result unreadCount() {
        return privateMessageService.unreadCount();
    }

    @GetMapping("/users")
    public Result searchUsers(@RequestParam(value = "keyword", required = false) String keyword) {
        return privateMessageService.searchUsers(keyword);
    }
}
