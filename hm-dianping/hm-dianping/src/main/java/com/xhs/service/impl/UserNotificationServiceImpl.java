package com.xhs.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhs.dto.Result;
import com.xhs.dto.UserDTO;
import com.xhs.dto.UserNotificationSettingRequest;
import com.xhs.entity.UserNotification;
import com.xhs.entity.UserNotificationSetting;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.mapper.UserNotificationMapper;
import com.xhs.mapper.UserNotificationSettingMapper;
import com.xhs.service.IUserNotificationService;
import com.xhs.service.UserNotificationPushService;
import com.xhs.utils.RedisConstants;
import com.xhs.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 用户消息通知服务实现。
 */
@Service
public class UserNotificationServiceImpl extends ServiceImpl<UserNotificationMapper, UserNotification> implements IUserNotificationService {
    @Resource
    private UserNotificationSettingMapper settingMapper;
    @Resource
    private UserNotificationPushService pushService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void notifyUser(Long userId, Long actorUserId, String type, String title, String content, Long blogId, Long orderId) {
        notifyUser(userId, actorUserId, type, title, content, blogId, orderId, "{}");
    }

    @Override
    public void notifyUser(Long userId, Long actorUserId, String type, String title, String content, Long blogId, Long orderId, String payload) {
        if (userId == null || (actorUserId != null && Objects.equals(userId, actorUserId))) {
            return;
        }
        String category = notificationCategory(type);
        UserNotificationSetting setting = getOrCreateSetting(userId);
        if (!categoryEnabled(setting, category)) {
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
        if (Boolean.TRUE.equals(setting.getRealtimeEnabled())) {
            pushService.push(notification, unreadCount(userId));
        }
    }

    @Override
    public Result listMine(Boolean unreadOnly, String category) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        var wrapper = query()
                .eq("user_id", user.getId())
                .eq(Boolean.TRUE.equals(unreadOnly), "read_flag", false);
        applyCategoryFilter(wrapper, category);
        return Result.ok(wrapper
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
        result.put("count", unreadCount(user.getId()));
        return Result.ok(result);
    }

    @Override
    public Result settings() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return Result.ok(getOrCreateSetting(user.getId()));
    }

    @Override
    public Result updateSettings(UserNotificationSettingRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        UserNotificationSetting setting = getOrCreateSetting(user.getId());
        if (request != null) {
            if (request.getInteractionEnabled() != null) setting.setInteractionEnabled(request.getInteractionEnabled());
            if (request.getOrderEnabled() != null) setting.setOrderEnabled(request.getOrderEnabled());
            if (request.getAuditEnabled() != null) setting.setAuditEnabled(request.getAuditEnabled());
            if (request.getSystemEnabled() != null) setting.setSystemEnabled(request.getSystemEnabled());
            if (request.getRealtimeEnabled() != null) setting.setRealtimeEnabled(request.getRealtimeEnabled());
        }
        setting.setUpdateTime(LocalDateTime.now());
        settingMapper.updateById(setting);
        return Result.ok(setting);
    }

    @Override
    public SseEmitter stream(String token) {
        UserDTO user = userByToken(token);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        UserNotificationSetting setting = getOrCreateSetting(user.getId());
        if (!Boolean.TRUE.equals(setting.getRealtimeEnabled())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实时通知已关闭");
        }
        return pushService.connect(user.getId());
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

    private UserNotificationSetting getOrCreateSetting(Long userId) {
        UserNotificationSetting setting = settingMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserNotificationSetting>()
                        .eq("user_id", userId)
                        .last("limit 1"));
        if (setting != null) {
            fillDefaultSetting(setting);
            return setting;
        }
        setting = defaultSetting(userId);
        settingMapper.insert(setting);
        return setting;
    }

    private UserNotificationSetting defaultSetting(Long userId) {
        return new UserNotificationSetting()
                .setUserId(userId)
                .setInteractionEnabled(true)
                .setOrderEnabled(true)
                .setAuditEnabled(true)
                .setSystemEnabled(true)
                .setRealtimeEnabled(true)
                .setUpdateTime(LocalDateTime.now());
    }

    private void fillDefaultSetting(UserNotificationSetting setting) {
        if (setting.getInteractionEnabled() == null) setting.setInteractionEnabled(true);
        if (setting.getOrderEnabled() == null) setting.setOrderEnabled(true);
        if (setting.getAuditEnabled() == null) setting.setAuditEnabled(true);
        if (setting.getSystemEnabled() == null) setting.setSystemEnabled(true);
        if (setting.getRealtimeEnabled() == null) setting.setRealtimeEnabled(true);
    }

    private boolean categoryEnabled(UserNotificationSetting setting, String category) {
        return switch (category) {
            case "interaction" -> Boolean.TRUE.equals(setting.getInteractionEnabled());
            case "order" -> Boolean.TRUE.equals(setting.getOrderEnabled());
            case "audit" -> Boolean.TRUE.equals(setting.getAuditEnabled());
            case "system" -> Boolean.TRUE.equals(setting.getSystemEnabled());
            default -> true;
        };
    }

    private String notificationCategory(String type) {
        String value = StrUtil.blankToDefault(type, "").toUpperCase();
        if (value.startsWith("ORDER_")) return "order";
        if (value.startsWith("AUDIT_")) return "audit";
        if (value.equals("LIKE") || value.equals("COLLECT") || value.equals("COMMENT")
                || value.equals("REPLY") || value.equals("FOLLOW")) {
            return "interaction";
        }
        return "system";
    }

    private void applyCategoryFilter(com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper<UserNotification> wrapper,
                                     String category) {
        String normalized = StrUtil.blankToDefault(category, "all").trim().toLowerCase();
        switch (normalized) {
            case "interaction" -> wrapper.in("type", "LIKE", "COLLECT", "COMMENT", "REPLY", "FOLLOW");
            case "order" -> wrapper.likeRight("type", "ORDER_");
            case "audit" -> wrapper.likeRight("type", "AUDIT_");
            case "system" -> wrapper.notLikeRight("type", "ORDER_")
                    .notLikeRight("type", "AUDIT_")
                    .notIn("type", "LIKE", "COLLECT", "COMMENT", "REPLY", "FOLLOW");
            default -> {
            }
        }
    }

    private long unreadCount(Long userId) {
        return query().eq("user_id", userId).eq("read_flag", false).count();
    }

    private UserDTO userByToken(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        if (userMap.isEmpty()) {
            return null;
        }
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    }
}
