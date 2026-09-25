package com.pulseops.ai;

import com.pulseops.config.PulseOpsProperties;
import com.pulseops.incident.IncidentEvents.AnalysisRequested;
import com.pulseops.incident.IncidentEvents.IncidentOpened;
import com.pulseops.incident.IncidentEvents.IncidentResolved;
import com.pulseops.incident.IncidentEvents.PostmortemRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands AI work to the bounded AI thread pool once the incident change has committed.
 * The committing thread (a Kafka consumer or an HTTP request) only pays for a queue offer, never for the Gemini call.
 */
@Component
public class AnalysisListener {

    private static final Logger log = LoggerFactory.getLogger(AnalysisListener.class);

    private final AnalysisService analysisService;
    private final ThreadPoolTaskExecutor aiExecutor;
    private final boolean enabled;

    public AnalysisListener(AnalysisService analysisService, ThreadPoolTaskExecutor aiExecutor, PulseOpsProperties properties) {
        this.analysisService = analysisService;
        this.aiExecutor = aiExecutor;
        this.enabled = properties.gemini().enabled();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOpened(IncidentOpened event) {
        if (enabled) {
            submitAnalysis(event.incidentId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisRequested(AnalysisRequested event) {
        submitAnalysis(event.incidentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResolved(IncidentResolved event) {
        if (enabled) {
            try {
                analysisService.markPostmortemPending(event.incidentId());
            } catch (RuntimeException e) {
                log.error("Could not mark post-mortem pending for incident {}", event.incidentId(), e);
            }
            submitPostmortem(event.incidentId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostmortemRequested(PostmortemRequested event) {
        submitPostmortem(event.incidentId());
    }

    private void submitAnalysis(Long incidentId) {
        try {
            aiExecutor.execute(() -> analysisService.analyze(incidentId));
        } catch (TaskRejectedException e) {
            log.warn("AI queue full, analysis for incident {} rejected", incidentId);
            try {
                analysisService.markFailed(incidentId, "AI queue is full. Retry in a minute.", 0);
            } catch (RuntimeException inner) {
                log.error("Could not mark analysis failed for incident {}", incidentId, inner);
            }
        }
    }

    private void submitPostmortem(Long incidentId) {
        try {
            aiExecutor.execute(() -> analysisService.writePostmortem(incidentId));
        } catch (TaskRejectedException e) {
            log.warn("AI queue full, post-mortem for incident {} rejected", incidentId);
        }
    }
}
