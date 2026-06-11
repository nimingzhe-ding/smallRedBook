package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.compensation.CompensationEventStatus;
import com.hmdp.compensation.CompensationEventTypes;
import com.hmdp.config.CompensationProperties;
import com.hmdp.config.CanalSyncProperties;
import com.hmdp.danmaku.DanmakuEvent;
import com.hmdp.entity.Blog;
import com.hmdp.entity.CompensationEvent;
import com.hmdp.entity.User;
import com.hmdp.entity.VideoDanmaku;
import com.hmdp.enums.ContentType;
import com.hmdp.mapper.CompensationEventMapper;
import com.hmdp.service.CompensationEventService;
import com.hmdp.service.DanmakuCacheRepairService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.service.IVideoDanmakuService;
import com.hmdp.service.PrivateMessagePushDispatchService;
import com.hmdp.service.SearchIndexService;
import com.hmdp.service.SeckillRedisRollbackService;
import com.hmdp.service.storage.FileStorageService;
import com.hmdp.utils.RedisConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationEventServiceImpl
        extends ServiceImpl<CompensationEventMapper, CompensationEvent>
        implements CompensationEventService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final CompensationProperties properties;
    private final CanalSyncProperties canalSyncProperties;
    private final SearchIndexService searchIndexService;
    private final IBlogService blogService;
    private final IUserService userService;
    private final IVideoDanmakuService videoDanmakuService;
    private final DanmakuCacheRepairService danmakuCacheRepairService;
    private final FileStorageService fileStorageService;
    private final SeckillRedisRollbackService seckillRedisRollbackService;
    private final PrivateMessagePushDispatchService privateMessagePushDispatchService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeterRegistry meterRegistry;

    @Override
    public void record(String eventType, String bizType, String bizId, String eventKey, Object payload,
                       String errorMessage) {
        record(eventType, bizType, bizId, eventKey, payload, errorMessage, LocalDateTime.now(), null);
    }

    @Override
    @Transactional
    public void record(String eventType, String bizType, String bizId, String eventKey, Object payload,
                       String errorMessage, LocalDateTime nextRetryTime, Integer maxRetry) {
        if (!properties.isEnabled() || StrUtil.hasBlank(eventType, eventKey)) {
            return;
        }
        String payloadJson = toJson(payload);
        LocalDateTime now = LocalDateTime.now();
        String safeError = shorten(errorMessage, 2000);
        CompensationEvent existing = lambdaQuery().eq(CompensationEvent::getEventKey, eventKey).one();
        if (existing == null) {
            CompensationEvent event = new CompensationEvent()
                    .setEventKey(eventKey)
                    .setEventType(eventType)
                    .setBizType(StrUtil.blankToDefault(bizType, "UNKNOWN"))
                    .setBizId(StrUtil.blankToDefault(bizId, ""))
                    .setPayload(payloadJson)
                    .setStatus(CompensationEventStatus.PENDING)
                    .setRetryCount(0)
                    .setMaxRetry(maxRetry == null ? properties.getMaxRetry() : maxRetry)
                    .setNextRetryTime(nextRetryTime == null ? now : nextRetryTime)
                    .setLastError(safeError)
                    .setCreateTime(now)
                    .setUpdateTime(now);
            try {
                save(event);
            } catch (DuplicateKeyException e) {
                rescheduleExisting(eventType, bizType, bizId, eventKey, payloadJson, safeError, nextRetryTime, maxRetry);
            }
        } else {
            rescheduleExisting(eventType, bizType, bizId, eventKey, payloadJson, safeError, nextRetryTime, maxRetry);
        }
        counter("hmdp.compensation.event.recorded", eventType).increment();
    }

    @Override
    @Transactional
    public void cancelByKey(String eventKey, String reason) {
        if (StrUtil.isBlank(eventKey)) {
            return;
        }
        update(new UpdateWrapper<CompensationEvent>()
                .set("status", CompensationEventStatus.CANCELED)
                .set("last_error", shorten(reason, 2000))
                .set("locked_until", null)
                .set("update_time", LocalDateTime.now())
                .eq("event_key", eventKey)
                .ne("status", CompensationEventStatus.SUCCEEDED));
    }

    @Override
    public boolean replayNow(Long eventId) {
        CompensationEvent event = getById(eventId);
        if (event == null || CompensationEventStatus.SUCCEEDED.equals(event.getStatus())
                || CompensationEventStatus.CANCELED.equals(event.getStatus())) {
            return false;
        }
        return replayOne(event, true);
    }

    @Override
    public int replayDue(Integer limit) {
        if (!properties.isEnabled()) {
            return 0;
        }
        int batchSize = Math.max(1, Math.min(limit == null ? properties.getBatchSize() : limit, 200));
        LocalDateTime now = LocalDateTime.now();
        List<CompensationEvent> events = list(new QueryWrapper<CompensationEvent>()
                .and(wrapper -> wrapper
                        .in("status", List.of(CompensationEventStatus.PENDING, CompensationEventStatus.FAILED))
                        .or(processing -> processing
                                .eq("status", CompensationEventStatus.PROCESSING)
                                .le("locked_until", now)))
                .le("next_retry_time", now)
                .orderByAsc("next_retry_time")
                .last("LIMIT " + batchSize));
        int count = 0;
        for (CompensationEvent event : events) {
            if (event.getRetryCount() != null && event.getMaxRetry() != null
                    && event.getRetryCount() >= event.getMaxRetry()) {
                continue;
            }
            if (replayOne(event, false)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Page<CompensationEvent> pageEvents(String status, String eventType, Integer current, Integer size) {
        int pageNo = current == null || current < 1 ? 1 : current;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        QueryWrapper<CompensationEvent> wrapper = new QueryWrapper<CompensationEvent>()
                .orderByDesc("update_time")
                .orderByDesc("id");
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        if (StrUtil.isNotBlank(eventType)) {
            wrapper.eq("event_type", eventType.trim().toUpperCase());
        }
        return page(new Page<>(pageNo, pageSize), wrapper);
    }

    private void rescheduleExisting(String eventType, String bizType, String bizId, String eventKey,
                                    String payloadJson, String safeError, LocalDateTime nextRetryTime,
                                    Integer maxRetry) {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<CompensationEvent> wrapper = new UpdateWrapper<CompensationEvent>()
                .set("event_type", eventType)
                .set("biz_type", StrUtil.blankToDefault(bizType, "UNKNOWN"))
                .set("biz_id", StrUtil.blankToDefault(bizId, ""))
                .set("payload", payloadJson)
                .set("status", CompensationEventStatus.PENDING)
                .set("next_retry_time", nextRetryTime == null ? now : nextRetryTime)
                .set("locked_until", null)
                .set("last_error", safeError)
                .set("update_time", now)
                .eq("event_key", eventKey);
        if (maxRetry != null) {
            wrapper.set("max_retry", maxRetry);
        }
        update(wrapper);
    }

    private boolean replayOne(CompensationEvent event, boolean force) {
        LocalDateTime now = LocalDateTime.now();
        if (!claim(event, now, force)) {
            return false;
        }
        try {
            execute(event);
            update(new UpdateWrapper<CompensationEvent>()
                    .set("status", CompensationEventStatus.SUCCEEDED)
                    .set("locked_until", null)
                    .set("last_error", null)
                    .set("update_time", LocalDateTime.now())
                    .eq("id", event.getId()));
            counter("hmdp.compensation.event.succeeded", event.getEventType()).increment();
            return true;
        } catch (Exception e) {
            int retryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
            int maxRetry = event.getMaxRetry() == null ? properties.getMaxRetry() : event.getMaxRetry();
            LocalDateTime nextRetryTime = retryCount >= maxRetry
                    ? LocalDateTime.now().plusSeconds(properties.getMaxBackoffSeconds())
                    : LocalDateTime.now().plusSeconds(backoffSeconds(retryCount));
            update(new UpdateWrapper<CompensationEvent>()
                    .set("status", CompensationEventStatus.FAILED)
                    .set("retry_count", retryCount)
                    .set("next_retry_time", nextRetryTime)
                    .set("locked_until", null)
                    .set("last_error", shorten(rootMessage(e), 2000))
                    .set("update_time", LocalDateTime.now())
                    .eq("id", event.getId()));
            counter("hmdp.compensation.event.failed", event.getEventType()).increment();
            log.warn("Compensation replay failed, id={}, type={}, retry={}/{}",
                    event.getId(), event.getEventType(), retryCount, maxRetry, e);
            return false;
        }
    }

    private boolean claim(CompensationEvent event, LocalDateTime now, boolean force) {
        UpdateWrapper<CompensationEvent> wrapper = new UpdateWrapper<CompensationEvent>()
                .set("status", CompensationEventStatus.PROCESSING)
                .set("locked_until", now.plusSeconds(properties.getLockSeconds()))
                .set("update_time", now)
                .eq("id", event.getId());
        if (!force) {
            wrapper.and(condition -> condition
                    .in("status", List.of(CompensationEventStatus.PENDING, CompensationEventStatus.FAILED))
                    .or(processing -> processing
                            .eq("status", CompensationEventStatus.PROCESSING)
                            .le("locked_until", now)));
        }
        return update(wrapper);
    }

    private void execute(CompensationEvent event) throws Exception {
        Map<String, Object> payload = payload(event);
        switch (event.getEventType()) {
            case CompensationEventTypes.DANMAKU_KAFKA_PERSIST -> persistDanmaku(payload);
            case CompensationEventTypes.DANMAKU_REDIS_ZSET_REFRESH ->
                    danmakuCacheRepairService.refreshVideoDanmakuCache(requiredLong(payload, "blogId"));
            case CompensationEventTypes.REDIS_VIDEO_CACHE_REFRESH -> refreshVideoCache(requiredLong(payload, "blogId"));
            case CompensationEventTypes.REDIS_VIDEO_CACHE_EVICT -> evictVideoCache(requiredLong(payload, "blogId"));
            case CompensationEventTypes.REDIS_USER_CACHE_REFRESH -> refreshUserCache(requiredLong(payload, "userId"));
            case CompensationEventTypes.REDIS_USER_CACHE_EVICT -> evictUserCache(requiredLong(payload, "userId"));
            case CompensationEventTypes.SEARCH_INDEX_BLOG_UPSERT -> upsertBlogIndex(requiredLong(payload, "blogId"));
            case CompensationEventTypes.SEARCH_INDEX_BLOG_DELETE ->
                    require(searchIndexService.deleteBlog(requiredLong(payload, "blogId")), event);
            case CompensationEventTypes.SEARCH_INDEX_USER_UPSERT -> upsertUserIndex(requiredLong(payload, "userId"));
            case CompensationEventTypes.SEARCH_INDEX_USER_DELETE ->
                    require(searchIndexService.deleteUser(requiredLong(payload, "userId")), event);
            case CompensationEventTypes.SEARCH_INDEX_DANMAKU_UPSERT ->
                    upsertDanmakuIndex(requiredLong(payload, "danmakuId"));
            case CompensationEventTypes.SEARCH_INDEX_DANMAKU_DELETE ->
                    require(searchIndexService.deleteDanmaku(requiredLong(payload, "danmakuId")), event);
            case CompensationEventTypes.OSS_MULTIPART_ABORT ->
                    abortMultipart(String.valueOf(payload.get("objectName")), String.valueOf(payload.get("uploadId")));
            case CompensationEventTypes.SECKILL_REDIS_ROLLBACK ->
                    seckillRedisRollbackService.rollback(requiredLong(payload, "voucherId"), requiredLong(payload, "userId"));
            case CompensationEventTypes.PRIVATE_MESSAGE_WS_PUSH ->
                    privateMessagePushDispatchService.dispatch(requiredLong(payload, "messageId"),
                            String.valueOf(payload.getOrDefault("requestId", "")));
            default -> throw new IllegalArgumentException("Unsupported compensation event type: " + event.getEventType());
        }
    }

    private void persistDanmaku(Map<String, Object> payload) {
        DanmakuEvent danmakuEvent = objectMapper.convertValue(payload, DanmakuEvent.class);
        LocalDateTime createTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(danmakuEvent.getCreateTime() == null
                        ? System.currentTimeMillis()
                        : danmakuEvent.getCreateTime()),
                ZoneId.systemDefault()
        );
        long duplicate = videoDanmakuService.query()
                .eq("blog_id", danmakuEvent.getVideoId())
                .eq("user_id", danmakuEvent.getUserId())
                .eq("content", danmakuEvent.getContent())
                .eq("video_second", danmakuEvent.getVideoSecond() == null ? 0 : danmakuEvent.getVideoSecond())
                .eq("create_time", createTime)
                .count();
        if (duplicate == 0) {
            videoDanmakuService.save(new VideoDanmaku()
                    .setBlogId(danmakuEvent.getVideoId())
                    .setUserId(danmakuEvent.getUserId())
                    .setContent(danmakuEvent.getContent())
                    .setVideoSecond(danmakuEvent.getVideoSecond() == null ? 0 : danmakuEvent.getVideoSecond())
                    .setLane(danmakuEvent.getLane())
                    .setStatus(VideoDanmakuServiceImpl.STATUS_NORMAL)
                    .setCreateTime(createTime)
                    .setUpdateTime(createTime));
        }
        danmakuCacheRepairService.refreshVideoDanmakuCache(danmakuEvent.getVideoId());
    }

    private void refreshVideoCache(Long blogId) {
        Blog blog = blogService.getById(blogId);
        if (blog == null || !isVisibleVideo(blog)) {
            evictVideoCache(blogId);
            return;
        }
        cache(RedisConstants.CACHE_VIDEO_KEY + blogId, blog);
    }

    private void evictVideoCache(Long blogId) {
        stringRedisTemplate.delete(RedisConstants.CACHE_VIDEO_KEY + blogId);
        stringRedisTemplate.delete(RedisConstants.VIDEO_DANMAKU_ZSET_KEY + blogId);
    }

    private void refreshUserCache(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            evictUserCache(userId);
            return;
        }
        cache(RedisConstants.CACHE_USER_KEY + userId, userCacheView(user));
    }

    private void evictUserCache(Long userId) {
        stringRedisTemplate.delete(RedisConstants.CACHE_USER_KEY + userId);
    }

    private void cache(String key, Object value) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(value),
                    canalSyncProperties.getCacheTtlMinutes(),
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            throw new IllegalStateException("Redis cache write failed, key=" + key, e);
        }
    }

    private void upsertBlogIndex(Long blogId) {
        Blog blog = blogService.getById(blogId);
        if (blog == null || (blog.getStatus() != null && blog.getStatus() != 0)) {
            require(searchIndexService.deleteBlog(blogId), CompensationEventTypes.SEARCH_INDEX_BLOG_DELETE, blogId);
            return;
        }
        require(searchIndexService.indexBlog(blog), CompensationEventTypes.SEARCH_INDEX_BLOG_UPSERT, blogId);
    }

    private void upsertUserIndex(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            require(searchIndexService.deleteUser(userId), CompensationEventTypes.SEARCH_INDEX_USER_DELETE, userId);
            return;
        }
        require(searchIndexService.indexUser(user), CompensationEventTypes.SEARCH_INDEX_USER_UPSERT, userId);
    }

    private void upsertDanmakuIndex(Long danmakuId) {
        VideoDanmaku danmaku = videoDanmakuService.getById(danmakuId);
        if (danmaku == null || danmaku.getStatus() == null
                || danmaku.getStatus() != VideoDanmakuServiceImpl.STATUS_NORMAL
                || StrUtil.isBlank(danmaku.getContent())) {
            require(searchIndexService.deleteDanmaku(danmakuId), CompensationEventTypes.SEARCH_INDEX_DANMAKU_DELETE, danmakuId);
            return;
        }
        require(searchIndexService.indexDanmaku(danmaku), CompensationEventTypes.SEARCH_INDEX_DANMAKU_UPSERT, danmakuId);
    }

    private boolean isVisibleVideo(Blog blog) {
        return blog != null
                && blog.getId() != null
                && (blog.getStatus() == null || blog.getStatus() == 0)
                && ContentType.supportsDanmaku(blog.getContentType(), blog.getVideoUrl());
    }

    private Map<String, Object> userCacheView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("phone", user.getPhone());
        view.put("nickName", user.getNickName());
        view.put("icon", user.getIcon());
        view.put("role", user.getRole());
        view.put("createTime", user.getCreateTime());
        view.put("updateTime", user.getUpdateTime());
        return view;
    }

    private void abortMultipart(String objectName, String uploadId) {
        if (StrUtil.hasBlank(objectName, uploadId)) {
            return;
        }
        try {
            fileStorageService.abortMultipartUpload(objectName, uploadId);
        } catch (RuntimeException e) {
            String message = rootMessage(e);
            if (message != null && message.contains("NoSuchUpload")) {
                return;
            }
            throw e;
        }
    }

    private void require(boolean success, CompensationEvent event) {
        if (!success) {
            throw new IllegalStateException("Compensation action returned false, type=" + event.getEventType());
        }
    }

    private void require(boolean success, String eventType, Long id) {
        if (!success) {
            throw new IllegalStateException("Compensation action returned false, type=" + eventType + ", id=" + id);
        }
    }

    private Map<String, Object> payload(CompensationEvent event) throws Exception {
        if (StrUtil.isBlank(event.getPayload())) {
            return Map.of();
        }
        return objectMapper.readValue(event.getPayload(), MAP_TYPE);
    }

    private Long requiredLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StrUtil.isNotBlank(text)) {
            return Long.valueOf(text);
        }
        throw new IllegalArgumentException("Missing payload field: " + key);
    }

    private long backoffSeconds(int retryCount) {
        long seconds = properties.getInitialBackoffSeconds();
        for (int i = 1; i < retryCount; i++) {
            if (seconds >= properties.getMaxBackoffSeconds()) {
                return properties.getMaxBackoffSeconds();
            }
            seconds = Math.min(properties.getMaxBackoffSeconds(), seconds * 2);
        }
        return seconds;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalStateException("Compensation payload JSON encode failed", e);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.toString() : current.getMessage();
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private Counter counter(String name, String eventType) {
        return Counter.builder(name)
                .tag("eventType", StrUtil.blankToDefault(eventType, "UNKNOWN"))
                .register(meterRegistry);
    }
}
