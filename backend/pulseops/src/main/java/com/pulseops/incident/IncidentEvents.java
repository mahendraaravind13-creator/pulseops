package com.pulseops.incident;

/**
 * Domain events published inside a transaction and consumed with {@code @TransactionalEventListener(AFTER_COMMIT)}.
 * Listeners therefore only run for changes that really were committed, and slow side effects (Gemini, webhooks)
 * never hold a database transaction open.
 */
public final class IncidentEvents {

    private IncidentEvents() {
    }

    public record IncidentOpened(Long incidentId, Long tenantId) {
    }

    public record IncidentResolved(Long incidentId, Long tenantId, boolean automatic) {
    }

    public record AnalysisRequested(Long incidentId) {
    }

    public record PostmortemRequested(Long incidentId) {
    }
}
