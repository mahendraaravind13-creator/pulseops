package com.pulseops.ingest;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Fixed-window rate limiter backed by Redis, shared by every app instance.
 *
 * <p>Key {@code ratelimit:<tenantId>:<epochMinute>} counts requests in the current minute. INCR and EXPIRE run in
 * one Lua script so they are atomic: with two separate commands, a crash between them would leave a counter that
 * never expires, and the tenant would be blocked forever.
 *
 * <p>Known weakness of fixed windows: a client can send the full quota at 12:00:59 and again at 12:01:00, i.e. up to
 * 2x the limit across a boundary. A sliding window or token bucket fixes that at the cost of more state. For
 * protecting the ingest pipeline from a misbehaving agent, the fixed window is good enough and easy to reason about.
 *
 * <p>Failure policy: if Redis is unavailable the limiter fails OPEN (allows the request). Blocking all telemetry
 * because the limiter's storage is down would turn a cache outage into a monitoring outage.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final Clock clock;

    public RateLimiter(StringRedisTemplate redis, MeterRegistry meters, Clock clock) {
        this.redis = redis;
        this.meters = meters;
        this.clock = clock;
    }

    public record Decision(boolean allowed, long count, int retryAfterSeconds) {
    }

    public Decision tryAcquire(long tenantId, int limitPerMinute) {
        long epochSecond = clock.instant().getEpochSecond();
        long window = epochSecond / 60;
        int retryAfter = (int) (60 - epochSecond % 60);
        String key = "ratelimit:" + tenantId + ":" + window;
        try {
            Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), "120");
            long current = count == null ? 0 : count;
            return new Decision(current <= limitPerMinute, current, retryAfter);
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            meters.counter("pulseops.ratelimit.errors").increment();
            return new Decision(true, 0, 0);
        }
    }
}
