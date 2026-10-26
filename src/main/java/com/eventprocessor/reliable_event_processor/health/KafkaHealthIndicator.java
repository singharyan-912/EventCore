package com.eventprocessor.reliable_event_processor.health;

import com.eventprocessor.reliable_event_processor.config.ConsumerProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator for Kafka connectivity and configuration.
 *
 * Checks broker reachability bounded by a strict 2-second timeout.
 * Explicitly distinguishes broker reachability from consumer execution status.
 * Never leaks credentials or sensitive configuration.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);

    private final KafkaAdmin kafkaAdmin;
    private final ConsumerProperties consumerProperties;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin, ConsumerProperties consumerProperties) {
        this.kafkaAdmin = kafkaAdmin;
        this.consumerProperties = consumerProperties;
    }

    @Override
    public Health health() {
        AdminClient adminClient = null;
        try {
            adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties());
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            String clusterId = clusterResult.clusterId().get(2, TimeUnit.SECONDS);
            int nodeCount = clusterResult.nodes().get(2, TimeUnit.SECONDS).size();

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodes", nodeCount)
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("consumerConfigured", consumerProperties.isEnabled())
                    .withDetail("consumerGroupId", consumerProperties.getGroupId())
                    .withDetail("note", "Broker reachable; consumer listener active if enabled (consumption state depends on topic traffic)")
                    .build();
        } catch (Exception ex) {
            log.warn("Kafka health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("reason", "Kafka cluster unreachable within 2s timeout")
                    .build();
        } finally {
            if (adminClient != null) {
                try {
                    adminClient.close(Duration.ofSeconds(1));
                } catch (Exception ignored) {
                }
            }
        }
    }
}
