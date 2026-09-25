package com.pulseops.evaluation;

import com.pulseops.ingest.SampleMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Hot-path SQL for incoming samples. Plain JDBC rather than JPA because these statements rely on PostgreSQL
 * features (ON CONFLICT upserts) and run for every sample, where a single explicit statement is clearest.
 */
@Repository
public class SampleStore {

    private final JdbcTemplate jdbc;

    public SampleStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registers the service on its first sample and moves last_seen_at forward on later ones.
     * GREATEST keeps last_seen_at correct when an older sample arrives after a newer one.
     */
    public long upsertService(long tenantId, String name, String hostname, Instant seenAt) {
        return jdbc.queryForObject("""
                INSERT INTO services (tenant_id, name, hostname, last_seen_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (tenant_id, name) DO UPDATE
                   SET last_seen_at = GREATEST(services.last_seen_at, EXCLUDED.last_seen_at),
                       hostname     = COALESCE(EXCLUDED.hostname, services.hostname)
                RETURNING id
                """, Long.class, tenantId, name, hostname, Timestamp.from(seenAt));
    }

    /**
     * Idempotent insert: returns false when this (service, recordedAt) is already stored, i.e. the message is a
     * Kafka redelivery or an agent retry. The caller then skips evaluation so a duplicate can never double-count.
     */
    public boolean insertSample(long serviceId, SampleMessage m) {
        int rows = jdbc.update("""
                INSERT INTO metric_samples (service_id, recorded_at, cpu, memory, disk, latency_ms, error_rate)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (service_id, recorded_at) DO NOTHING
                """, serviceId, Timestamp.from(m.recordedAt()), m.cpu(), m.memory(), m.disk(), m.latencyMs(), m.errorRate());
        return rows == 1;
    }

    /** Samples of one service in [from, to], oldest first. Served by the (service_id, recorded_at) unique index. */
    public List<SamplePoint> window(long serviceId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT recorded_at, cpu, memory, disk, latency_ms, error_rate
                  FROM metric_samples
                 WHERE service_id = ? AND recorded_at BETWEEN ? AND ?
                 ORDER BY recorded_at
                """, (rs, n) -> new SamplePoint(rs.getTimestamp("recorded_at").toInstant(),
                        (Double) rs.getObject("cpu"), (Double) rs.getObject("memory"), (Double) rs.getObject("disk"),
                        (Double) rs.getObject("latency_ms"), (Double) rs.getObject("error_rate")),
                serviceId, Timestamp.from(from), Timestamp.from(to));
    }
}
