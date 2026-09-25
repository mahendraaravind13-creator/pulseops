package com.pulseops.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * AI work runs in memory after commit. If the instance restarts mid-call, the row would stay PENDING forever.
 * This job turns such rows into FAILED so the UI offers a retry. Idempotent, so it is safe on every instance.
 */
@Component
public class AnalysisSweeper {

    private static final Logger log = LoggerFactory.getLogger(AnalysisSweeper.class);
    private static final Duration STUCK_AFTER = Duration.ofMinutes(5);

    private final AiAnalysisRepository analyses;
    private final Clock clock;

    public AnalysisSweeper(AiAnalysisRepository analyses, Clock clock) {
        this.analyses = analyses;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT2M")
    @Transactional
    public void failStuckWork() {
        Instant now = clock.instant();
        int analysesFixed = analyses.failStuckAnalyses(now.minus(STUCK_AFTER), now);
        int postmortemsFixed = analyses.failStuckPostmortems(now.minus(STUCK_AFTER), now);
        if (analysesFixed + postmortemsFixed > 0) {
            log.info("Marked {} analyses and {} post-mortems as failed after being stuck", analysesFixed, postmortemsFixed);
        }
    }
}
