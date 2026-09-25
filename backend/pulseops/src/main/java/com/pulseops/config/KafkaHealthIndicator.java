package com.pulseops.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Adds Kafka to /actuator/health (Spring Boot has built-in indicators for the database and Redis, not Kafka).
 * The app cannot accept telemetry without the broker, so "Kafka unreachable" must make the instance unhealthy.
 */
@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult cluster = admin.describeCluster(new DescribeClusterOptions().timeoutMs(2000));
            int brokers = cluster.nodes().get(3, TimeUnit.SECONDS).size();
            return Health.up().withDetail("clusterId", cluster.clusterId().get(3, TimeUnit.SECONDS))
                    .withDetail("brokers", brokers).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage()).build();
        }
    }
}
