package com.eventprocessor.reliable_event_processor.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConfig {

    private final OutboxProperties outboxProperties;
    private final ConsumerProperties consumerProperties;

    public KafkaConfig(OutboxProperties outboxProperties,
                       ConsumerProperties consumerProperties) {
        this.outboxProperties = outboxProperties;
        this.consumerProperties = consumerProperties;
    }

    /**
     * Configures KafkaAdmin so that startup does not fail if the Kafka broker is temporarily offline.
     */
    @Bean
    public KafkaAdmin admin(KafkaProperties properties) {
        KafkaAdmin admin = new KafkaAdmin(properties.buildAdminProperties(null));
        admin.setFatalIfBrokerNotAvailable(false);
        admin.setAutoCreate(false);
        return admin;
    }

    /**
     * Declares the default inventory-events topic with 3 partitions and 1 replica.
     */
    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(outboxProperties.getTopic())
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declares the Dead-Letter Topic (DLT) for malformed/unrecoverable messages.
     */
    @Bean
    public NewTopic inventoryEventsDltTopic() {
        return TopicBuilder.name(consumerProperties.getDltTopic())
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Configures the Kafka listener container factory with MANUAL_IMMEDIATE acknowledgment mode.
     * Guarantees offsets are committed only after successful business processing or durable handoff.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
