package com.pulseops.incident;

import com.pulseops.ai.AnalysisStatus;
import com.pulseops.ai.PostmortemStatus;
import com.pulseops.rules.Metric;
import com.pulseops.rules.Operator;
import com.pulseops.rules.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class IncidentDtos {

    private IncidentDtos() {
    }

    public record IncidentSummary(Long id, String title, IncidentStatus status, Severity severity,
                                  Long serviceId, String serviceName, Long ruleId, String ruleName, Metric metric,
                                  Instant openedAt, Instant acknowledgedAt, Instant resolvedAt, Instant lastBreachAt,
                                  double peakValue, int breachCount, long version) {
    }

    public record Condition(Metric metric, Operator operator, double threshold, int durationSeconds) {
    }

    public record EventView(Long id, IncidentEventType type, String message, String actor, Instant createdAt) {
    }

    public record AnalysisView(AnalysisStatus status, String rootCause, Integer confidence, List<String> suggestedActions,
                               String model, String error, String postmortem, PostmortemStatus postmortemStatus,
                               Instant updatedAt) {
    }

    public record IncidentDetail(Long id, String title, IncidentStatus status, Severity severity,
                                 Long serviceId, String serviceName, Long ruleId, String ruleName, Metric metric,
                                 Instant openedAt, Instant acknowledgedAt, Instant resolvedAt, Instant lastBreachAt,
                                 double peakValue, int breachCount, long version,
                                 Condition condition, String resolutionNote, String acknowledgedBy, String resolvedBy,
                                 boolean autoResolved, List<EventView> events, AnalysisView analysis) {
    }

    public record VersionRequest(@NotNull Long version) {
    }

    public record ResolveRequest(@NotNull Long version, @Size(max = 2000) String note) {
    }

    public record NoteRequest(@NotBlank @Size(max = 2000) String message) {
    }
}
