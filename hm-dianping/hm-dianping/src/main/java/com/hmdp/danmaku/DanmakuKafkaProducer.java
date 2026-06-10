package com.hmdp.danmaku;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.DanmakuKafkaProperties;
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

    public void sendAsync(DanmakuEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(properties.getTopic(), event.getVideoId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Send danmaku Kafka message failed, messageId={}, videoId={}",
                                    event.getMessageId(), event.getVideoId(), ex);
                        }
                    });
        } catch (Exception e) {
            log.error("Serialize danmaku Kafka message failed, messageId={}, videoId={}",
                    event.getMessageId(), event.getVideoId(), e);
        }
    }
}
