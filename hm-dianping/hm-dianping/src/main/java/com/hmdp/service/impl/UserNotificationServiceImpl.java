package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.UserNotification;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.UserNotificationMapper;
import com.hmdp.service.IUserNotificationService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 用户消息通知服务实现。
 */
@Service
public class UserNotificationServiceImpl extends ServiceImpl<UserNotificationMapper, UserNotification> implements IUserNotificationService {

    @Override
    public void notifyUser(Long userId, Long actorUserId, String type, String title, String content, Long blogId, Long orderId) {
        notifyUser(userId, actorUserId, type, title, content, blogId, orderId, "{}");
    }

    @Override
    public void notifyUser(Long userId, Long actorUserId, String type, String title, String content, Long blogId, Long orderId, String payload) {
        if (userId == null || (actorUserId != null && Objects.equals(userId, actorUserId))) {
            return;
        }
        UserNotification notification = new UserNotification()
                .setUserId(userId)
                .setActorUserId(actorUserId)
                .setType(type)
                .setTitle(title)
                .setContent(content)
                .setBlogId(blogId)
                .setOrderId(orderId)
                .setPayload(payload == null || payload.isBlank() ? "{}" : payload)
                .setReadFlag(false)
                .setCreateTime(LocalDateTime.now());
        save(notification);
    }

    @Override
    public Result listMine(Boolean unreadOnly) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return Result.ok(query()
                .eq("user_id", user.getId())
                .eq(Boolean.TRUE.equals(unreadOnly), "read_flag", false)
                .orderByAsc("read_flag")
                .orderByDesc("create_time")
                .last("limit 50")
                .list());
    }

    @Override
    public Result unreadCount() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.ok(Map.of("count", 0L));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", query().eq("user_id", user.getId()).eq("read_flag", false).count());
        return Result.ok(result);
    }

    @Override
    public Result markAllRead() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        update()
                .set("read_flag", true)
                .eq("user_id", user.getId())
                .eq("read_flag", false)
                .update();
        return Result.ok();
    }

    @Override
    public Result markRead(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        update()
                .set("read_flag", true)
                .eq("id", id)
                .eq("user_id", user.getId())
                .update();
        return Result.ok();
    }

    @Override
    public Result deleteOne(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        remove(query().eq("id", id).eq("user_id", user.getId()).getWrapper());
        return Result.ok();
    }
}
