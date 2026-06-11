package com.xhs.service;

import com.xhs.entity.UserNotification;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户通知 SSE 推送通道。
 */
@Service
public class UserNotificationPushService {
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("type", "connected", "userId", userId)));
        } catch (Exception e) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    public void push(UserNotification notification, Long unreadCount) {
        if (notification == null || notification.getUserId() == null) {
            return;
        }
        Set<SseEmitter> userEmitters = emitters.get(notification.getUserId());
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "type", "notification",
                "unreadCount", unreadCount == null ? 0L : unreadCount,
                "notification", notification
        );
        for (SseEmitter emitter : List.copyOf(userEmitters)) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload));
            } catch (Exception e) {
                removeEmitter(notification.getUserId(), emitter);
            }
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        userEmitters.remove(emitter);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId);
        }
    }
}
