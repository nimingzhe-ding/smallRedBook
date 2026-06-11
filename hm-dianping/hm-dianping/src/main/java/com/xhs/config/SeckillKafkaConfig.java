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
public class SeckillKafkaConfig {

    @Bean
    public NewTopic seckillOrderTopic(SeckillKafkaProperties properties) {
        return new NewTopic(properties.getOrderTopic(), properties.getPartitions(), properties.getReplicas());
    }

    @Bean
    public NewTopic seckillOrderDltTopic(SeckillKafkaProperties properties) {
        return new NewTopic(properties.getOrderDltTopic(), properties.getPartitions(), properties.getReplicas());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            SeckillKafkaProperties properties) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(properties.getListenerConcurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(seckillOrderErrorHandler(kafkaTemplate, properties));
        return factory;
    }

    private DefaultErrorHandler seckillOrderErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            SeckillKafkaProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(properties.getOrderDltTopic(), record.partition())
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
