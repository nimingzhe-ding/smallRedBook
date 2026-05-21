package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IUserNotificationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记社区消息通知接口。
 */
@RestController
@RequestMapping("/notifications")
public class NotificationsController {
    @Resource
    private IUserNotificationService notificationService;

    @GetMapping
    public Result list(@RequestParam(value = "unreadOnly", defaultValue = "false") Boolean unreadOnly) {
        return notificationService.listMine(unreadOnly);
    }

    @GetMapping("/unread-count")
    public Result unreadCount() {
        return notificationService.unreadCount();
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
