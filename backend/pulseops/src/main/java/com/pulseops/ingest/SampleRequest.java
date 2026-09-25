package com.pulseops.ingest;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * What an agent sends. Note what is NOT here: no tenant id (it comes from the API key) and no "status"
 * (deciding whether something is wrong is the server's job, done by alert rules).
 */
public record SampleRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "letters, digits, '.', '_' or '-' only")
        String service,
        @Size(max = 255) String hostname,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double cpu,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double memory,
        @DecimalMin("0") @DecimalMax("100") Double disk,
        @DecimalMin("0") @DecimalMax("600000") Double latencyMs,
        @DecimalMin("0") @DecimalMax("100") Double errorRate,
        Instant recordedAt) {
}
