package com.xhs.controller;

import com.xhs.dto.Result;
import com.xhs.dto.UserNotificationSettingRequest;
import com.xhs.service.IUserNotificationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 笔记社区消息通知接口。
 */
@RestController
@RequestMapping("/notifications")
public class NotificationsController {
    @Resource
    private IUserNotificationService notificationService;

    @GetMapping
    public Result list(@RequestParam(value = "unreadOnly", defaultValue = "false") Boolean unreadOnly,
                       @RequestParam(value = "category", required = false) String category) {
        return notificationService.listMine(unreadOnly, category);
    }

    @GetMapping("/unread-count")
    public Result unreadCount() {
        return notificationService.unreadCount();
    }

    @GetMapping("/settings")
    public Result settings() {
        return notificationService.settings();
    }

    @PutMapping("/settings")
    public Result updateSettings(@RequestBody UserNotificationSettingRequest request) {
        return notificationService.updateSettings(request);
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam("token") String token) {
        return notificationService.stream(token);
    }

    @PostMapping("/read")
    public Result markAllRead() {
        return notificationService.markAllRead();
    }

    @PostMapping("/{id}/read")
    public Result markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        return notificationService.deleteOne(id);
    }
}
