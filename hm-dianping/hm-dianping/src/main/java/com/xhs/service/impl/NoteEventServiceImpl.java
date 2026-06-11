package com.xhs.service.impl;

import com.xhs.entity.NoteEvent;
import com.xhs.entity.Blog;
import com.xhs.enums.EventType;
import com.xhs.mapper.NoteEventMapper;
import com.xhs.service.IBlogService;
import com.xhs.service.INoteEventService;
import com.xhs.utils.RedisConstants;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class NoteEventServiceImpl implements INoteEventService {
    private static final String EVENT_GROUP = "note-event-db";
    private static final String EVENT_CONSUMER = "note-event-consumer-1";

    @Resource
    private NoteEventMapper noteEventMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IBlogService blogService;

    private final ExecutorService noteEventExecutor = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        initStreamGroup();
        noteEventExecutor.submit(new NoteEventHandler());
    }

    @PreDestroy
    private void destroy() {
        noteEventExecutor.shutdownNow();
    }

    @Override
    public void track(Long userId, Long blogId, EventType eventType, String scene, String keyword) {
        NoteEvent event = new NoteEvent();
        event.setUserId(userId);
        event.setBlogId(blogId);
        event.setEventType(eventType.name());
        event.setScene(scene);
        event.setKeyword(keyword);
        event.setCreateTime(LocalDateTime.now());
        updateRealtimeSignals(event);
        try {
            stringRedisTemplate.opsForStream().add(RedisConstants.NOTE_EVENT_STREAM_KEY, toStreamMap(event));
        } catch (Exception e) {
            log.warn("行为事件写入 Redis Stream 失败，降级落库: userId={}, blogId={}, type={}, error={}",
                    userId, blogId, eventType, e.getMessage());
            saveEvent(event);
        }
    }

    @Override
    public void trackBatch(List<NoteEvent> events) {
        if (events == null || events.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (NoteEvent event : events) {
            if (event == null || event.getEventType() == null) {
                continue;
            }
            event.setId(null);
            if (event.getBlogId() == null) {
                event.setBlogId(event.getNoteId());
            }
            event.setEventType(event.getEventType().toUpperCase());
            if (event.getCreateTime() == null) {
                event.setCreateTime(now);
            }
            updateRealtimeSignals(event);
            try {
                stringRedisTemplate.opsForStream().add(RedisConstants.NOTE_EVENT_STREAM_KEY, toStreamMap(event));
            } catch (Exception e) {
                log.warn("批量事件写入 Stream 失败，降级落库: event={}, error={}", event, e.getMessage());
                saveEvent(event);
            }
        }
    }

    private Map<String, String> toStreamMap(NoteEvent event) {
        Map<String, String> values = new HashMap<>();
        put(values, "userId", event.getUserId());
        put(values, "blogId", event.getBlogId());
        put(values, "eventType", event.getEventType());
        put(values, "scene", event.getScene());
        put(values, "keyword", event.getKeyword());
        put(values, "createTime", event.getCreateTime() == null ? LocalDateTime.now() : event.getCreateTime());
        return values;
    }

    private void put(Map<String, String> values, String key, Object value) {
        if (value != null) {
            values.put(key, String.valueOf(value));
        }
    }

    private NoteEvent fromStreamRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        NoteEvent event = new NoteEvent();
        event.setUserId(toLong(values.get("userId")));
        event.setBlogId(toLong(values.get("blogId")));
        event.setEventType(toString(values.get("eventType")));
        event.setScene(toString(values.get("scene")));
        event.setKeyword(toString(values.get("keyword")));
        event.setCreateTime(toLocalDateTime(values.get("createTime")));
        return event;
    }

    private void saveEvent(NoteEvent event) {
        try {
            event.setId(null);
            if (event.getCreateTime() == null) {
                event.setCreateTime(LocalDateTime.now());
            }
            noteEventMapper.insert(event);
        } catch (Exception e) {
            log.warn("行为事件落库失败: event={}, error={}", event, e.getMessage());
        }
    }

    private void updateRealtimeSignals(NoteEvent event) {
        if (event == null || StrUtil.isBlank(event.getEventType())) {
            return;
        }
        EventType type;
        try {
            type = EventType.valueOf(event.getEventType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        updateHotSearch(type, event.getKeyword());
        updateUserInterest(event.getUserId(), event.getBlogId(), event.getKeyword(), type);
    }

    private void updateHotSearch(EventType type, String keyword) {
        if (type != EventType.SEARCH || StrUtil.isBlank(keyword)) {
            return;
        }
        try {
            String normalizedKeyword = StrUtil.sub(StrUtil.trim(keyword), 0, 64);
            stringRedisTemplate.opsForZSet().incrementScore(RedisConstants.HOT_SEARCH_KEY, normalizedKeyword, 1);
            stringRedisTemplate.expire(RedisConstants.HOT_SEARCH_KEY,
                    RedisConstants.HOT_SEARCH_TTL, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("更新热搜缓存失败: keyword={}, error={}", keyword, e.getMessage());
        }
    }

    private void updateUserInterest(Long userId, Long blogId, String keyword, EventType type) {
        if (userId == null) {
            return;
        }
        int weight = eventWeight(type);
        if (weight == 0) {
            return;
        }
        try {
            String key = RedisConstants.USER_INTEREST_KEY + userId;
            if (type == EventType.SEARCH && StrUtil.isNotBlank(keyword)) {
                stringRedisTemplate.opsForZSet().incrementScore(key, StrUtil.sub(StrUtil.trim(keyword), 0, 32), weight);
            }
            if (blogId != null) {
                Blog blog = blogService.getById(blogId);
                if (blog != null) {
                    for (String tag : extractInterestWords(blog)) {
                        stringRedisTemplate.opsForZSet().incrementScore(key, tag, weight);
                    }
                }
            }
            stringRedisTemplate.expire(key, RedisConstants.USER_INTEREST_TTL, java.util.concurrent.TimeUnit.DAYS);
        } catch (Exception e) {
            log.debug("更新用户兴趣失败: userId={}, blogId={}, type={}, error={}",
                    userId, blogId, type, e.getMessage());
        }
    }

    private int eventWeight(EventType type) {
        return switch (type) {
            case SEARCH -> 1;
            case IMPRESSION -> 0;
            case CLICK, DETAIL, PLAY, DWELL -> 2;
            case LIKE, COMMENT, SHARE -> 3;
            case COLLECT, PURCHASE -> 5;
            case UNCOLLECT -> -3;
        };
    }

    private java.util.List<String> extractInterestWords(Blog blog) {
        java.util.LinkedHashSet<String> words = new java.util.LinkedHashSet<>();
        addCsvWords(words, blog.getTags());
        addTopicWords(words, blog.getContent());
        if (StrUtil.isNotBlank(blog.getContentType())) {
            words.add(blog.getContentType());
        }
        return words.stream().limit(8).toList();
    }

    private void addCsvWords(java.util.Set<String> words, String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        for (String item : value.split(",")) {
            String word = StrUtil.sub(StrUtil.trim(item), 0, 32);
            if (StrUtil.isNotBlank(word)) {
                words.add(word);
            }
        }
    }

    private void addTopicWords(java.util.Set<String> words, String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("#([\\p{IsHan}\\w\\-]{1,30})")
                .matcher(content);
        while (matcher.find()) {
            words.add(matcher.group(1));
        }
    }

    private void initStreamGroup() {
        try {
            Boolean exists = stringRedisTemplate.hasKey(RedisConstants.NOTE_EVENT_STREAM_KEY);
            if (!Boolean.TRUE.equals(exists)) {
                stringRedisTemplate.opsForStream().add(RedisConstants.NOTE_EVENT_STREAM_KEY, Map.of("init", "1"));
            }
            stringRedisTemplate.opsForStream().createGroup(RedisConstants.NOTE_EVENT_STREAM_KEY, ReadOffset.latest(), EVENT_GROUP);
        } catch (RedisSystemException e) {
            if (!isConsumerGroupAlreadyExists(e)) {
                log.warn("初始化笔记事件 Stream 失败，将在请求侧降级落库: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("初始化笔记事件 Stream 失败，将在请求侧降级落库: {}", e.getMessage());
        }
    }

    private boolean isConsumerGroupAlreadyExists(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void ack(MapRecord<String, Object, Object> record) {
        stringRedisTemplate.opsForStream().acknowledge(RedisConstants.NOTE_EVENT_STREAM_KEY, EVENT_GROUP, record.getId());
    }

    private void handleRecord(MapRecord<String, Object, Object> record) {
        if (record.getValue().containsKey("init")) {
            ack(record);
            return;
        }
        saveEvent(fromStreamRecord(record));
        ack(record);
    }

    private class NoteEventHandler implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from(EVENT_GROUP, EVENT_CONSUMER),
                            StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                            StreamOffset.create(RedisConstants.NOTE_EVENT_STREAM_KEY, ReadOffset.lastConsumed())
                    );
                    if (records == null || records.isEmpty()) {
                        handlePending();
                        continue;
                    }
                    records.forEach(NoteEventServiceImpl.this::handleRecord);
                } catch (Exception e) {
                    log.warn("消费笔记事件 Stream 异常，准备处理 pending-list: {}", e.getMessage());
                    handlePending();
                }
            }
        }

        private void handlePending() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from(EVENT_GROUP, EVENT_CONSUMER),
                            StreamReadOptions.empty().count(10),
                            StreamOffset.create(RedisConstants.NOTE_EVENT_STREAM_KEY, ReadOffset.from("0"))
                    );
                    if (records == null || records.isEmpty()) {
                        break;
                    }
                    records.forEach(NoteEventServiceImpl.this::handleRecord);
                } catch (Exception e) {
                    log.warn("处理笔记事件 pending-list 异常: {}", e.getMessage());
                    break;
                }
            }
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }
}
