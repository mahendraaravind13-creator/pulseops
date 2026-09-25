package com.pulseops.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.config.PulseOpsProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes samples to Kafka and waits (briefly) for the broker's acknowledgement.
 *
 * <p>Why wait? Returning 202 before Kafka confirmed the write would mean "accepted" could still be lost.
 * With {@code acks=all} the ack means the record is durably stored. If the broker does not answer within 3s,
 * the agent gets a 503 and retries. The retry may produce a duplicate if the first write did land late, which is
 * harmless: the agent resends the same recordedAt, and the consumer's INSERT ... ON CONFLICT ignores it.
 *
 * <p>Why not write to Postgres here too? Writing to two systems in one request (the "dual write" problem) can leave
 * them inconsistent if the second write fails. Kafka is the single entry point; the consumer does the DB write.
 */
@Component
public class SampleProducer {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public SampleProducer(KafkaTemplate<String, String> kafka, ObjectMapper mapper, PulseOpsProperties properties) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = properties.kafka().samplesTopic();
    }

    public void publish(SampleMessage message) {
        String json;
        try {
            json = mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize sample", e);
        }
        try {
            kafka.send(topic, message.partitionKey(), json).get(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QueueUnavailableException("Interrupted while publishing", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new QueueUnavailableException("Telemetry queue unavailable", e);
        }
    }
}
