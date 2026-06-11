package com.xhs.danmaku;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.compensation.CompensationEventTypes;
import com.xhs.config.DanmakuKafkaProperties;
import com.xhs.entity.VideoDanmaku;
import com.xhs.service.CompensationEventService;
import com.xhs.service.IVideoDanmakuService;
import com.xhs.service.impl.VideoDanmakuServiceImpl;
import com.xhs.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuPersistenceConsumer {

    private final ObjectMapper objectMapper;
    private final IVideoDanmakuService videoDanmakuService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DanmakuKafkaProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final CompensationEventService compensationEventService;

    @KafkaListener(
            topics = "${xiaohongshu.danmaku.kafka.topic:video-danmaku}",
            groupId = "${xiaohongshu.danmaku.kafka.consumer-group:video-danmaku-consumer-group}",
            containerFactory = "danmakuKafkaListenerContainerFactory"
    )
    public void handleDanmakuMessages(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment)
            throws Exception {
        if (records == null || records.isEmpty()) {
            acknowledgment.acknowledge();
            return;
        }

        List<DanmakuEvent> events = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            try {
                events.add(objectMapper.readValue(record.value(), DanmakuEvent.class));
            } catch (Exception e) {
                log.warn("Skip invalid danmaku Kafka message, topic={}, partition={}, offset={}, payload={}",
                        record.topic(), record.partition(), record.offset(), record.value(), e);
            }
        }
        if (events.isEmpty()) {
            acknowledgment.acknowledge();
            return;
        }

        List<VideoDanmaku> entities = events.stream().map(this::toEntity).toList();
        transactionTemplate.executeWithoutResult(status -> videoDanmakuService.saveBatch(entities));
        cacheSavedDanmaku(events, entities);
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = "${xiaohongshu.danmaku.kafka.dlt-topic:video-danmaku.DLT}",
            groupId = "video-danmaku-dlt-compensation-group",
            containerFactory = "danmakuKafkaListenerContainerFactory"
    )
    public void handleDanmakuDltMessages(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        if (records == null || records.isEmpty()) {
            acknowledgment.acknowledge();
            return;
        }
        for (ConsumerRecord<String, String> record : records) {
            try {
                DanmakuEvent event = objectMapper.readValue(record.value(), DanmakuEvent.class);
                compensationEventService.record(
                        CompensationEventTypes.DANMAKU_KAFKA_PERSIST,
                        "DANMAKU",
                        String.valueOf(event.getVideoId()),
                        danmakuPersistKey(event, record),
                        event,
                        "Danmaku Kafka message entered DLT"
                );
            } catch (Exception e) {
                log.error("Skip invalid danmaku DLT message, topic={}, partition={}, offset={}, payload={}",
                        record.topic(), record.partition(), record.offset(), record.value(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private VideoDanmaku toEntity(DanmakuEvent event) {
        LocalDateTime createTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.getCreateTime() == null ? System.currentTimeMillis() : event.getCreateTime()),
                ZoneId.systemDefault()
        );
        return new VideoDanmaku()
                .setBlogId(event.getVideoId())
                .setUserId(event.getUserId())
                .setContent(event.getContent())
                .setVideoSecond(event.getVideoSecond() == null ? 0 : event.getVideoSecond())
                .setLane(event.getLane())
                .setStatus(VideoDanmakuServiceImpl.STATUS_NORMAL)
                .setCreateTime(createTime)
                .setUpdateTime(createTime);
    }

    private void cacheSavedDanmaku(List<DanmakuEvent> events, List<VideoDanmaku> entities) {
        try {
            for (int i = 0; i < events.size(); i++) {
                DanmakuEvent event = events.get(i);
                VideoDanmaku entity = entities.get(i);
                event.setId(entity.getId());
                event.setBlogId(entity.getBlogId());
                String key = RedisConstants.VIDEO_DANMAKU_ZSET_KEY + event.getVideoId();
                stringRedisTemplate.opsForZSet().add(
                        key,
                        objectMapper.writeValueAsString(event),
                        event.getVideoSecond() == null ? 0D : event.getVideoSecond().doubleValue()
                );
                stringRedisTemplate.expire(key, properties.getCacheTtlHours(), TimeUnit.HOURS);
                trimCache(key);
            }
        } catch (Exception e) {
            log.warn("Update danmaku Redis ZSet failed after database batch save", e);
            Set<Long> blogIds = new LinkedHashSet<>();
            for (DanmakuEvent event : events) {
                Long blogId = event.getVideoId() == null ? event.getBlogId() : event.getVideoId();
                if (blogId != null) {
                    blogIds.add(blogId);
                }
            }
            for (Long blogId : blogIds) {
                compensationEventService.record(
                        CompensationEventTypes.DANMAKU_REDIS_ZSET_REFRESH,
                        "DANMAKU",
                        String.valueOf(blogId),
                        "danmaku:redis-zset:refresh:" + blogId,
                        Map.of("blogId", blogId),
                        e.getMessage()
                );
            }
        }
    }

    private String danmakuPersistKey(DanmakuEvent event, ConsumerRecord<String, String> record) {
        if (event.getRequestId() != null && !event.getRequestId().isBlank()) {
            return "danmaku:persist:request:" + event.getRequestId();
        }
        if (event.getMessageId() != null) {
            return "danmaku:persist:message:" + event.getMessageId();
        }
        return "danmaku:persist:kafka:" + record.topic() + ":" + record.partition() + ":" + record.offset();
    }

    private void trimCache(String key) {
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        if (size == null || size <= properties.getCacheLimit()) {
            return;
        }
        long overflow = size - properties.getCacheLimit();
        stringRedisTemplate.opsForZSet().removeRange(key, 0, overflow - 1);
    }
}
