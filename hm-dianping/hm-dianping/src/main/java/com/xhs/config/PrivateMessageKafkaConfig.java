package com.xhs.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class PrivateMessageKafkaConfig {

    @Bean
    public NewTopic privateMessageTopic(PrivateMessageKafkaProperties properties) {
        return new NewTopic(properties.getTopic(), properties.getPartitions(), properties.getReplicas());
    }

    @Bean
    public NewTopic privateMessageDltTopic(PrivateMessageKafkaProperties properties) {
        return new NewTopic(properties.getDltTopic(), properties.getPartitions(), properties.getReplicas());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> privateMessageKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            PrivateMessageKafkaProperties properties) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getListenerConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(privateMessageErrorHandler(kafkaTemplate, properties));
        return factory;
    }

    private DefaultErrorHandler privateMessageErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            PrivateMessageKafkaProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(properties.getDltTopic(), record.partition())
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
