package com.pulseops.ingest;

import com.pulseops.common.BadRequestException;
import com.pulseops.tenant.TenantAuth;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * The agent-facing endpoint. By the time a request reaches this method it has passed API-key authentication and the
 * per-tenant rate limit (both servlet filters). The handler only validates and enqueues, so it answers in a few
 * milliseconds no matter how slow rule evaluation or the database is.
 */
@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(60);
    private static final Duration MAX_AGE = Duration.ofDays(1);

    private final SampleProducer producer;
    private final MeterRegistry meters;
    private final Clock clock;

    public IngestController(SampleProducer producer, MeterRegistry meters, Clock clock) {
        this.producer = producer;
        this.meters = meters;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(@AuthenticationPrincipal TenantAuth tenant,
                                                      @Valid @RequestBody SampleRequest sample) {
        Instant now = clock.instant();
        Instant recordedAt = sample.recordedAt() == null ? now : sample.recordedAt();
        if (recordedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw new BadRequestException("recordedAt is in the future; check the agent's clock");
        }
        if (recordedAt.isBefore(now.minus(MAX_AGE))) {
            throw new BadRequestException("recordedAt is older than 24 hours");
        }
        producer.publish(new SampleMessage(SampleMessage.CURRENT_VERSION, tenant.tenantId(), sample.service(),
                sample.hostname(), sample.cpu(), sample.memory(), sample.disk(), sample.latencyMs(),
                sample.errorRate(), recordedAt));
        meters.counter("pulseops.samples.ingested").increment();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("accepted", true));
    }

    @ExceptionHandler(QueueUnavailableException.class)
    ResponseEntity<ProblemDetail> queueDown(QueueUnavailableException e) {
        meters.counter("pulseops.samples.rejected", "reason", "queue_unavailable").increment();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "Telemetry queue is temporarily unavailable. Retry shortly."));
    }
}
