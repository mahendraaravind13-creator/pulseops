package com.pulseops.services;

import com.pulseops.common.BadRequestException;
import com.pulseops.common.NotFoundException;
import com.pulseops.config.PulseOpsProperties;
import com.pulseops.evaluation.SnapshotStore;
import com.pulseops.evaluation.SnapshotStore.Snapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Current health of each service (for the overview grid) and time-series data (for charts).
 *
 * <p>Status is derived, never stored: CRITICAL/WARNING if the service has an active incident of that severity,
 * STALE if it stopped reporting, otherwise HEALTHY. Deriving it means it can never drift out of sync with incidents.
 */
@Service
public class ServiceHealthService {

    public enum HealthStatus { CRITICAL, WARNING, STALE, HEALTHY }

    public record Latest(Double cpu, Double memory, Double disk, Double latencyMs, Double errorRate, Instant recordedAt) {
    }

    public record ServiceHealth(Long id, String name, String hostname, Instant lastSeenAt, HealthStatus status,
                                long openIncidents, Latest latest) {
    }

    public record MetricPoint(Instant t, Double cpu, Double memory, Double disk, Double latencyMs, Double errorRate) {
    }

    public record MetricSeries(String range, int bucketSeconds, List<MetricPoint> points) {
    }

    /** Range → bucket size, chosen so every chart has at most a few hundred points. */
    private static final Map<String, Duration[]> RANGES = Map.of(
            "15m", new Duration[]{Duration.ofMinutes(15), Duration.ofSeconds(10)},
            "1h", new Duration[]{Duration.ofHours(1), Duration.ofSeconds(30)},
            "6h", new Duration[]{Duration.ofHours(6), Duration.ofMinutes(2)},
            "24h", new Duration[]{Duration.ofHours(24), Duration.ofMinutes(5)},
            "7d", new Duration[]{Duration.ofDays(7), Duration.ofMinutes(30)});

    private final JdbcTemplate jdbc;
    private final SnapshotStore snapshots;
    private final MonitoredServiceRepository services;
    private final Duration staleAfter;
    private final Clock clock;

    public ServiceHealthService(JdbcTemplate jdbc, SnapshotStore snapshots, MonitoredServiceRepository services,
                                PulseOpsProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.snapshots = snapshots;
        this.services = services;
        this.staleAfter = properties.monitoring().staleAfter();
        this.clock = clock;
    }

    private record Row(Long id, String name, String hostname, Instant lastSeenAt, long openIncidents, int worst) {
    }

    @Transactional(readOnly = true)
    public List<ServiceHealth> list(Long tenantId) {
        List<Row> rows = jdbc.query("""
                SELECT s.id, s.name, s.hostname, s.last_seen_at,
                       count(i.id) AS open_incidents,
                       coalesce(max(CASE i.severity WHEN 'CRITICAL' THEN 2 WHEN 'WARNING' THEN 1 END), 0) AS worst
                  FROM services s
                  LEFT JOIN incidents i ON i.service_id = s.id AND i.status <> 'RESOLVED'
                 WHERE s.tenant_id = ?
                 GROUP BY s.id
                 ORDER BY s.name
                """, (rs, n) -> new Row(rs.getLong("id"), rs.getString("name"), rs.getString("hostname"),
                rs.getTimestamp("last_seen_at") == null ? null : rs.getTimestamp("last_seen_at").toInstant(),
                rs.getLong("open_incidents"), rs.getInt("worst")), tenantId);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Row::id).toList();
        Map<Long, Latest> latest = latestValues(tenantId, ids);
        Instant now = clock.instant();
        return rows.stream().map(r -> new ServiceHealth(r.id(), r.name(), r.hostname(), r.lastSeenAt(),
                status(r, now), r.openIncidents(), latest.get(r.id()))).toList();
    }

    @Transactional(readOnly = true)
    public ServiceHealth get(Long tenantId, Long serviceId) {
        return list(tenantId).stream().filter(s -> s.id().equals(serviceId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Service", serviceId));
    }

    @Transactional(readOnly = true)
    public MetricSeries metrics(Long tenantId, Long serviceId, String range) {
        services.findByIdAndTenantId(serviceId, tenantId).orElseThrow(() -> new NotFoundException("Service", serviceId));
        Duration[] spec = RANGES.get(range == null ? "1h" : range);
        if (spec == null) {
            throw new BadRequestException("range must be one of 15m, 1h, 6h, 24h, 7d");
        }
        Instant from = clock.instant().minus(spec[0]);
        int bucketSeconds = (int) spec[1].toSeconds();
        List<MetricPoint> points = jdbc.query("""
                SELECT date_bin(make_interval(secs => ?), recorded_at, TIMESTAMPTZ '2000-01-01') AS t,
                       avg(cpu) AS cpu, avg(memory) AS memory, avg(disk) AS disk,
                       avg(latency_ms) AS latency_ms, avg(error_rate) AS error_rate
                  FROM metric_samples
                 WHERE service_id = ? AND recorded_at >= ?
                 GROUP BY 1
                 ORDER BY 1
                """, (rs, n) -> new MetricPoint(rs.getTimestamp("t").toInstant(), round(rs.getObject("cpu")),
                        round(rs.getObject("memory")), round(rs.getObject("disk")), round(rs.getObject("latency_ms")),
                        round(rs.getObject("error_rate"))),
                bucketSeconds, serviceId, Timestamp.from(from));
        return new MetricSeries(range == null ? "1h" : range, bucketSeconds, points);
    }

    /** Redis first; any service missing from Redis (expired, or Redis down) is read from Postgres. */
    private Map<Long, Latest> latestValues(Long tenantId, List<Long> ids) {
        Map<Long, Latest> result = new HashMap<>();
        snapshots.read(tenantId, ids).forEach((id, s) -> result.put(id, fromSnapshot(s)));
        List<Long> missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            String placeholders = String.join(",", missing.stream().map(x -> "?").toList());
            jdbc.query("""
                    SELECT DISTINCT ON (service_id) service_id, recorded_at, cpu, memory, disk, latency_ms, error_rate
                      FROM metric_samples
                     WHERE service_id IN (%s)
                     ORDER BY service_id, recorded_at DESC
                    """.formatted(placeholders), rs -> {
                result.put(rs.getLong("service_id"), new Latest((Double) rs.getObject("cpu"),
                        (Double) rs.getObject("memory"), (Double) rs.getObject("disk"),
                        (Double) rs.getObject("latency_ms"), (Double) rs.getObject("error_rate"),
                        rs.getTimestamp("recorded_at").toInstant()));
            }, missing.toArray());
        }
        return result;
    }

    private HealthStatus status(Row r, Instant now) {
        if (r.worst() == 2) return HealthStatus.CRITICAL;
        if (r.worst() == 1) return HealthStatus.WARNING;
        if (r.lastSeenAt() == null || r.lastSeenAt().isBefore(now.minus(staleAfter))) return HealthStatus.STALE;
        return HealthStatus.HEALTHY;
    }

    private static Latest fromSnapshot(Snapshot s) {
        return new Latest(s.cpu(), s.memory(), s.disk(), s.latencyMs(), s.errorRate(), s.recordedAt());
    }

    private static Double round(Object value) {
        return value == null ? null : Math.round(((Number) value).doubleValue() * 10) / 10.0;
    }
}
