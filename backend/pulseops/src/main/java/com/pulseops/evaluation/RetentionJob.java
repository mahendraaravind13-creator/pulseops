package com.pulseops.evaluation;

import com.pulseops.config.PulseOpsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Deletes samples older than the retention period, in batches.
 *
 * <p>Why batches? One DELETE of millions of rows holds locks and generates WAL for a long time and can stall other
 * writers. Small batches, each its own short transaction, keep the table responsive.
 *
 * <p>Why no distributed lock even though every app instance runs this job? The delete is idempotent: if both
 * instances run at once, they delete disjoint or already-deleted rows, and the end state is the same.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private static final int BATCH_SIZE = 5_000;
    private static final int MAX_BATCHES_PER_RUN = 200;

    private final JdbcTemplate jdbc;
    private final int retentionDays;
    private final Clock clock;

    public RetentionJob(JdbcTemplate jdbc, PulseOpsProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.retentionDays = properties.monitoring().retentionDays();
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void purgeOldSamples() {
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        long total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int deleted = jdbc.update("""
                    DELETE FROM metric_samples
                     WHERE id IN (SELECT id FROM metric_samples WHERE recorded_at < ? LIMIT ?)
                    """, Timestamp.from(cutoff), BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("Retention: deleted {} samples older than {}", total, cutoff);
        }
    }
}
