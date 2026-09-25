package com.pulseops.ingest;

import java.time.Instant;

/**
 * The Kafka record value (JSON) on topic {@code telemetry.samples}. The record key is {@code tenantId:service},
 * so every sample of one service goes to the same partition and is evaluated in order.
 * {@code schemaVersion} lets the consumer handle old and new formats during a rolling deploy.
 */
public record SampleMessage(int schemaVersion, long tenantId, String service, String hostname,
                            double cpu, double memory, Double disk, Double latencyMs, Double errorRate,
                            Instant recordedAt) {

    public static final int CURRENT_VERSION = 1;

    public String partitionKey() {
        return tenantId + ":" + service;
    }
}
