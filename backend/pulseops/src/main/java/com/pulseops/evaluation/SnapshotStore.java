package com.pulseops.evaluation;

import com.pulseops.config.PulseOpsProperties;
import com.pulseops.ingest.SampleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The latest sample per service, kept in a Redis hash {@code snapshot:<tenantId>:<serviceId>}.
 *
 * <p>The dashboard polls "current state of every service" every few seconds. Answering that from Postgres means a
 * "latest row per service" query over a table that grows by millions of rows a day. Redis turns it into one
 * pipelined HGETALL per service.
 *
 * <p>This is a derived view, not the source of truth: Postgres has every sample. If Redis is empty or down, readers
 * fall back to Postgres. The TTL makes a service that stopped reporting drop out of the snapshot on its own.
 */
@Component
public class SnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStore.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SnapshotStore(StringRedisTemplate redis, PulseOpsProperties properties) {
        this.redis = redis;
        this.ttl = properties.monitoring().snapshotTtl();
    }

    public record Snapshot(Double cpu, Double memory, Double disk, Double latencyMs, Double errorRate, Instant recordedAt) {
    }

    public void write(long tenantId, long serviceId, SampleMessage m) {
        Map<String, String> fields = new HashMap<>();
        fields.put("cpu", String.valueOf(m.cpu()));
        fields.put("memory", String.valueOf(m.memory()));
        putIfPresent(fields, "disk", m.disk());
        putIfPresent(fields, "latencyMs", m.latencyMs());
        putIfPresent(fields, "errorRate", m.errorRate());
        fields.put("recordedAt", m.recordedAt().toString());
        String key = key(tenantId, serviceId);
        try {
            redis.executePipelined((RedisCallback<Object>) connection -> {
                byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
                connection.keyCommands().del(rawKey); // drop fields from an older sample (e.g. disk no longer sent)
                connection.hashCommands().hMSet(rawKey, encode(fields));
                connection.keyCommands().expire(rawKey, ttl.toSeconds());
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Could not update snapshot for service {}: {}", serviceId, e.getMessage());
        }
    }

    /** Returns snapshots for the given services; services without a snapshot are absent from the map. */
    public Map<Long, Snapshot> read(long tenantId, List<Long> serviceIds) {
        Map<Long, Snapshot> result = new HashMap<>();
        if (serviceIds.isEmpty()) {
            return result;
        }
        try {
            List<Object> replies = redis.executePipelined((RedisCallback<Object>) connection -> {
                for (Long id : serviceIds) {
                    connection.hashCommands().hGetAll(key(tenantId, id).getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            for (int i = 0; i < serviceIds.size(); i++) {
                if (replies.get(i) instanceof Map<?, ?> map && !map.isEmpty()) {
                    result.put(serviceIds.get(i), parse(map));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Snapshot read failed, falling back to the database: {}", e.getMessage());
        }
        return result;
    }

    private static String key(long tenantId, long serviceId) {
        return "snapshot:" + tenantId + ":" + serviceId;
    }

    private static void putIfPresent(Map<String, String> fields, String name, Double value) {
        if (value != null) {
            fields.put(name, String.valueOf(value));
        }
    }

    private static Map<byte[], byte[]> encode(Map<String, String> fields) {
        Map<byte[], byte[]> raw = new HashMap<>();
        fields.forEach((k, v) -> raw.put(k.getBytes(StandardCharsets.UTF_8), v.getBytes(StandardCharsets.UTF_8)));
        return raw;
    }

    private static Snapshot parse(Map<?, ?> map) {
        return new Snapshot(number(map.get("cpu")), number(map.get("memory")), number(map.get("disk")),
                number(map.get("latencyMs")), number(map.get("errorRate")),
                map.get("recordedAt") == null ? null : Instant.parse(map.get("recordedAt").toString()));
    }

    private static Double number(Object value) {
        return value == null ? null : Double.valueOf(value.toString());
    }
}
