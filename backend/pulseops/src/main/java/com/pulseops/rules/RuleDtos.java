package com.pulseops.rules;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class RuleDtos {

    private RuleDtos() {
    }

    public record RuleRequest(
            @NotBlank @Size(max = 120) String name,
            Long serviceId,
            @NotNull Metric metric,
            @NotNull Operator operator,
            @NotNull @Min(0) @Max(1_000_000) Double threshold,
            @NotNull @Min(0) @Max(3600) Integer durationSeconds,
            @NotNull Severity severity,
            Boolean enabled) {
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    public record RuleResponse(Long id, String name, Long serviceId, String serviceName, Metric metric,
                               Operator operator, double threshold, int durationSeconds, Severity severity,
                               boolean enabled, long activeIncidents, Instant createdAt, Instant updatedAt) {
    }
}
