package com.xhs.privatemessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.compensation.CompensationEventTypes;
import com.xhs.service.CompensationEventService;
import com.xhs.service.PrivateMessagePushDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.LinkedHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrivateMessagePushConsumer {

    private final ObjectMapper objectMapper;
    private final PrivateMessagePushDispatchService dispatchService;
    private final CompensationEventService compensationEventService;

    @KafkaListener(
            topics = "${xiaohongshu.private-message.kafka.topic:private-message}",
            groupId = "${xiaohongshu.private-message.kafka.push-consumer-group:private-message-push-consumer-group}",
            containerFactory = "privateMessageKafkaListenerContainerFactory",
            properties = {"auto.offset.reset=latest"}
    )
    public void handlePushEvent(String payload, Acknowledgment acknowledgment) throws Exception {
        PrivateMessagePushEvent event = objectMapper.readValue(payload, PrivateMessagePushEvent.class);
        dispatchService.dispatch(event.getMessageId(), event.getRequestId());
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = "${xiaohongshu.private-message.kafka.dlt-topic:private-message.DLT}",
            groupId = "${xiaohongshu.private-message.kafka.dlt-consumer-group:private-message-dlt-compensation-group}",
            containerFactory = "privateMessageKafkaListenerContainerFactory"
    )
    public void handlePushDltEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            PrivateMessagePushEvent event = objectMapper.readValue(record.value(), PrivateMessagePushEvent.class);
            compensationEventService.record(
                    CompensationEventTypes.PRIVATE_MESSAGE_WS_PUSH,
                    "PRIVATE_MESSAGE",
                    String.valueOf(event.getMessageId()),
                    event.getEventKey() == null || event.getEventKey().isBlank()
                            ? "private-message:push:message:" + event.getMessageId()
                            : event.getEventKey(),
                    compensationPayload(event),
                    "Private message push entered DLT"
            );
        } catch (Exception e) {
            log.error("Handle private message DLT failed, topic={}, partition={}, offset={}, payload={}",
                    record.topic(), record.partition(), record.offset(), record.value(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private Map<String, Object> compensationPayload(PrivateMessagePushEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", event.getMessageId());
        payload.put("requestId", event.getRequestId() == null ? "" : event.getRequestId());
        return payload;
    }
}
