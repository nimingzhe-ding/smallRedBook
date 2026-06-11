package com.hmdp.privatemessage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.PrivateMessageKafkaProperties;
import com.hmdp.entity.PrivateMessageOutbox;
import com.hmdp.mapper.PrivateMessageOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrivateMessageOutboxRelay {

    private final PrivateMessageOutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PrivateMessageKafkaProperties properties;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${hmdp.private-message.kafka.outbox-fixed-delay-millis:1000}")
    public void publishDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<PrivateMessageOutbox> events = outboxMapper.selectList(new QueryWrapper<PrivateMessageOutbox>()
                .and(wrapper -> wrapper
                        .in("status", List.of(PrivateMessageOutboxStatus.PENDING, PrivateMessageOutboxStatus.FAILED))
                        .or(processing -> processing
                                .eq("status", PrivateMessageOutboxStatus.PROCESSING)
                                .le("locked_until", now)))
                .lt("retry_count", properties.getOutboxMaxRetry())
                .le("next_retry_time", now)
                .orderByAsc("next_retry_time")
                .last("LIMIT " + Math.max(1, properties.getOutboxBatchSize())));
        for (PrivateMessageOutbox event : events) {
            publishOne(event, now);
        }
    }

    private void publishOne(PrivateMessageOutbox event, LocalDateTime now) {
        if (!claim(event, now)) {
            return;
        }
        try {
            String payload = event.getPayload();
            if (payload == null || payload.isBlank()) {
                payload = objectMapper.writeValueAsString(toPushEvent(event));
            }
            kafkaTemplate.send(properties.getTopic(), event.getReceiverId().toString(), payload).get(3, TimeUnit.SECONDS);
            outboxMapper.update(null, new UpdateWrapper<PrivateMessageOutbox>()
                    .set("status", PrivateMessageOutboxStatus.SENT)
                    .set("locked_until", null)
                    .set("last_error", null)
                    .set("update_time", LocalDateTime.now())
                    .eq("id", event.getId()));
        } catch (Exception e) {
            int retryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
            outboxMapper.update(null, new UpdateWrapper<PrivateMessageOutbox>()
                    .set("status", PrivateMessageOutboxStatus.FAILED)
                    .set("retry_count", retryCount)
                    .set("next_retry_time", LocalDateTime.now().plusSeconds(backoffSeconds(retryCount)))
                    .set("locked_until", null)
                    .set("last_error", shorten(rootMessage(e), 2000))
                    .set("update_time", LocalDateTime.now())
                    .eq("id", event.getId()));
            log.warn("Publish private message outbox failed, outboxId={}, messageId={}, retry={}",
                    event.getId(), event.getMessageId(), retryCount, e);
        }
    }

    private boolean claim(PrivateMessageOutbox event, LocalDateTime now) {
        return outboxMapper.update(null, new UpdateWrapper<PrivateMessageOutbox>()
                .set("status", PrivateMessageOutboxStatus.PROCESSING)
                .set("locked_until", now.plusSeconds(properties.getOutboxLockSeconds()))
                .set("update_time", now)
                .eq("id", event.getId())
                .and(condition -> condition
                        .in("status", List.of(PrivateMessageOutboxStatus.PENDING, PrivateMessageOutboxStatus.FAILED))
                        .or(processing -> processing
                                .eq("status", PrivateMessageOutboxStatus.PROCESSING)
                                .le("locked_until", now)))) > 0;
    }

    private PrivateMessagePushEvent toPushEvent(PrivateMessageOutbox event) {
        return new PrivateMessagePushEvent()
                .setEventKey(event.getEventKey())
                .setMessageId(event.getMessageId())
                .setConversationId(event.getConversationId())
                .setSenderId(event.getSenderId())
                .setReceiverId(event.getReceiverId())
                .setRequestId(event.getRequestId())
                .setCreateTime(event.getCreateTime());
    }

    private long backoffSeconds(int retryCount) {
        long seconds = properties.getOutboxInitialBackoffSeconds();
        for (int i = 1; i < retryCount; i++) {
            if (seconds >= properties.getOutboxMaxBackoffSeconds()) {
                return properties.getOutboxMaxBackoffSeconds();
            }
            seconds = Math.min(properties.getOutboxMaxBackoffSeconds(), seconds * 2);
        }
        return seconds;
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
}
