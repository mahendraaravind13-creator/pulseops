package com.pulseops.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    /**
     * Three partitions: enough for two app instances (and three listener threads each) to share the work.
     * Partitions are the unit of parallelism in a consumer group; more consumers than partitions would sit idle.
     */
    @Bean
    NewTopic samplesTopic(PulseOpsProperties properties) {
        return TopicBuilder.name(properties.kafka().samplesTopic()).partitions(properties.kafka().partitions()).replicas(1).build();
    }

    /** Same partition count as the main topic, because failed records keep their original partition number. */
    @Bean
    NewTopic deadLetterTopic(PulseOpsProperties properties) {
        return TopicBuilder.name(properties.kafka().deadLetterTopic()).partitions(properties.kafka().partitions()).replicas(1).build();
    }

    /**
     * Failure handling for the sample listener:
     * transient errors (database hiccup, optimistic-lock conflict) are retried with exponential backoff
     * (0.5s, 1s, 2s); after that, or immediately for errors that can never succeed (malformed JSON), the record is
     * published to the dead-letter topic and the partition moves on. Nothing is silently dropped.
     * Spring Boot plugs this bean into the default listener container factory.
     */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, PulseOpsProperties properties) {
        String dlt = properties.kafka().deadLetterTopic();
        var recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> new TopicPartition(dlt, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxAttempts(3);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
