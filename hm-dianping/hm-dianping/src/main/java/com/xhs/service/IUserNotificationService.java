package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.Result;
import com.xhs.dto.UserNotificationSettingRequest;
import com.xhs.entity.UserNotification;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 用户消息通知服务。
 */
public interface IUserNotificationService extends IService<UserNotification> {
    void notifyUser(Long userId, Long actorUserId, String type, String title, String content, Long blogId, Long orderId);

    void notifyUser(Long userId, Long actorUserId, String type, String title, String content, Long blogId, Long orderId, String payload);

    Result listMine(Boolean unreadOnly, String category);

    Result unreadCount();

    Result settings();

    Result updateSettings(UserNotificationSettingRequest request);

    SseEmitter stream(String token);

    Result markAllRead();

    Result markRead(Long id);

    Result deleteOne(Long id);
}
