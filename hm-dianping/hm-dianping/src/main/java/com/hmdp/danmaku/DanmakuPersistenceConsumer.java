package com.hmdp.danmaku;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.DanmakuKafkaProperties;
import com.hmdp.entity.VideoDanmaku;
import com.hmdp.service.IVideoDanmakuService;
import com.hmdp.service.impl.VideoDanmakuServiceImpl;
import com.hmdp.utils.RedisConstants;
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
import java.util.List;
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

    @KafkaListener(
            topics = "${hmdp.danmaku.kafka.topic:video-danmaku}",
            groupId = "${hmdp.danmaku.kafka.consumer-group:video-danmaku-consumer-group}",
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
        }
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
