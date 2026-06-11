package com.xhs.danmaku;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.compensation.CompensationEventTypes;
import com.xhs.config.DanmakuKafkaProperties;
import com.xhs.service.CompensationEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DanmakuKafkaProperties properties;
    private final CompensationEventService compensationEventService;

    public void sendAsync(DanmakuEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(properties.getTopic(), event.getVideoId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Send danmaku Kafka message failed, messageId={}, videoId={}",
                                    event.getMessageId(), event.getVideoId(), ex);
                            recordPersistCompensation(event, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Serialize danmaku Kafka message failed, messageId={}, videoId={}",
                    event.getMessageId(), event.getVideoId(), e);
            recordPersistCompensation(event, e.getMessage());
        }
    }

    private void recordPersistCompensation(DanmakuEvent event, String reason) {
        try {
            compensationEventService.record(
                    CompensationEventTypes.DANMAKU_KAFKA_PERSIST,
                    "DANMAKU",
                    String.valueOf(event.getVideoId()),
                    danmakuPersistKey(event),
                    event,
                    reason
            );
        } catch (Exception e) {
            log.error("Record danmaku persist compensation failed, messageId={}, videoId={}",
                    event.getMessageId(), event.getVideoId(), e);
        }
    }

    private String danmakuPersistKey(DanmakuEvent event) {
        if (event.getRequestId() != null && !event.getRequestId().isBlank()) {
            return "danmaku:persist:request:" + event.getRequestId();
        }
        if (event.getMessageId() != null) {
            return "danmaku:persist:message:" + event.getMessageId();
        }
        return "danmaku:persist:" + event.getVideoId() + ":" + event.getUserId() + ":" + event.getCreateTime();
    }
}
