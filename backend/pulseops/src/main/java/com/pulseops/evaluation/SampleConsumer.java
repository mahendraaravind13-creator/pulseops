package com.pulseops.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.ingest.SampleMessage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for {@code telemetry.samples}, group {@code pulseops-evaluator}.
 *
 * <p>With two app instances and three partitions, Kafka assigns each partition to exactly one consumer in the group,
 * so a given service's samples are always processed by one thread, in order. If an instance dies, its partitions
 * are reassigned to the survivor (a rebalance) and processing continues from the last committed offset.
 *
 * <p>Delivery is at-least-once: the offset is committed after {@link SampleProcessor#process} returns. A crash after
 * the DB commit but before the offset commit causes a redelivery, which the idempotent sample insert absorbs.
 * Failures are retried and then sent to the dead-letter topic (see KafkaConfig), so one bad message cannot block
 * its partition forever.
 */
@Component
public class SampleConsumer {

    private final ObjectMapper mapper;
    private final SampleProcessor processor;
    private final SnapshotStore snapshots;
    private final MeterRegistry meters;

    public SampleConsumer(ObjectMapper mapper, SampleProcessor processor, SnapshotStore snapshots, MeterRegistry meters) {
        this.mapper = mapper;
        this.processor = processor;
        this.snapshots = snapshots;
        this.meters = meters;
    }

    @KafkaListener(topics = "${pulseops.kafka.samples-topic}")
    public void onSample(String payload) {
        SampleMessage sample = parse(payload);
        MDC.put("tenantId", String.valueOf(sample.tenantId()));
        try {
            SampleProcessor.Result result = Timer.builder("pulseops.samples.processing")
                    .register(meters)
                    .record(() -> processor.process(sample));
            if (result != null && !result.duplicate()) {
                // After the transaction committed: the snapshot never shows a sample that was rolled back.
                snapshots.write(sample.tenantId(), result.serviceId(), sample);
            }
            meters.counter("pulseops.samples.processed", "duplicate",
                    String.valueOf(result != null && result.duplicate())).increment();
        } finally {
            MDC.remove("tenantId");
        }
    }

    private SampleMessage parse(String payload) {
        try {
            SampleMessage sample = mapper.readValue(payload, SampleMessage.class);
            if (sample.schemaVersion() != SampleMessage.CURRENT_VERSION || sample.recordedAt() == null) {
                throw new IllegalArgumentException("Unsupported sample message: " + payload);
            }
            return sample;
        } catch (JsonProcessingException e) {
            // Not retryable: the same bytes will never parse. KafkaConfig routes this straight to the dead-letter topic.
            throw new IllegalArgumentException("Malformed sample message", e);
        }
    }
}
