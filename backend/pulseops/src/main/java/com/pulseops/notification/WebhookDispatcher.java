package com.pulseops.notification;

import com.pulseops.config.PulseOpsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Delivers queued webhooks with retries. The queue is the {@code webhook_deliveries} table.
 *
 * <p>Claiming work: {@code SELECT ... FOR UPDATE SKIP LOCKED} locks the rows this instance will send and makes other
 * instances skip them instead of waiting. Two instances polling at the same moment therefore split the work rather
 * than sending the same webhook twice. The locks are released when the transaction commits the new status.
 *
 * <p>Retries: exponential backoff (30s, 60s, 120s, ...) up to {@code pulseops.webhook.max-attempts}, then FAILED.
 * Receivers still may see a duplicate (e.g. we time out after they processed the request), so every request carries
 * an {@code Idempotency-Key} header that stays the same across retries; receivers can use it to deduplicate.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final int BATCH = 10;

    private final JdbcTemplate jdbc;
    private final RestClient http;
    private final int maxAttempts;
    private final Clock clock;

    public WebhookDispatcher(JdbcTemplate jdbc, PulseOpsProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.maxAttempts = properties.webhook().maxAttempts();
        this.clock = clock;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.webhook().timeout()).build());
        factory.setReadTimeout(properties.webhook().timeout());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    record Delivery(long id, String event, String url, String payload, int attempts) {
    }

    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT5S")
    @Transactional
    public void dispatchDue() {
        List<Delivery> due = jdbc.query("""
                SELECT id, event, url, payload, attempts
                  FROM webhook_deliveries
                 WHERE status = 'PENDING' AND next_attempt_at <= now()
                 ORDER BY next_attempt_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Delivery(rs.getLong("id"), rs.getString("event"), rs.getString("url"),
                rs.getString("payload"), rs.getInt("attempts")), BATCH);
        for (Delivery d : due) {
            send(d);
        }
    }

    private void send(Delivery d) {
        int attempt = d.attempts() + 1;
        Instant now = clock.instant();
        try {
            var response = http.post().uri(URI.create(d.url()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "pulseops-delivery-" + d.id())
                    .header("X-PulseOps-Event", d.event())
                    .body(d.payload())
                    .retrieve()
                    .toBodilessEntity();
            jdbc.update("""
                    UPDATE webhook_deliveries SET status = 'DELIVERED', attempts = ?, response_code = ?,
                           last_error = NULL, delivered_at = ? WHERE id = ?
                    """, attempt, response.getStatusCode().value(), Timestamp.from(now), d.id());
        } catch (RestClientResponseException e) {
            recordFailure(d, attempt, e.getStatusCode().value(), "HTTP " + e.getStatusCode().value(), now);
        } catch (RuntimeException e) {
            recordFailure(d, attempt, null, e.getClass().getSimpleName() + ": " + e.getMessage(), now);
        }
    }

    private void recordFailure(Delivery d, int attempt, Integer code, String error, Instant now) {
        boolean giveUp = attempt >= maxAttempts;
        Instant next = now.plus(Duration.ofSeconds(30L << Math.min(attempt - 1, 6)));
        jdbc.update("""
                UPDATE webhook_deliveries SET status = ?, attempts = ?, response_code = ?, last_error = ?,
                       next_attempt_at = ? WHERE id = ?
                """, giveUp ? "FAILED" : "PENDING", attempt, code, truncate(error), Timestamp.from(next), d.id());
        log.warn("Webhook delivery {} attempt {} failed: {}{}", d.id(), attempt, error, giveUp ? " (giving up)" : "");
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }
}
