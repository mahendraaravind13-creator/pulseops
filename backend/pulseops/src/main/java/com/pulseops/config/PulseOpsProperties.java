package com.pulseops.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * All PulseOps-specific settings in one typed object, bound from the {@code pulseops.*} keys in application.yml.
 * Validation runs at startup, so a missing JWT secret stops the app instead of failing on the first login.
 */
@Validated
@ConfigurationProperties("pulseops")
public record PulseOpsProperties(
        @NotNull Jwt jwt,
        @NotNull Cors cors,
        @NotNull Kafka kafka,
        @NotNull Monitoring monitoring,
        @NotNull Gemini gemini,
        @NotNull Webhook webhook) {

    public record Jwt(
            @NotBlank(message = "JWT_SECRET must be set (at least 32 characters)")
            @Size(min = 32, message = "JWT_SECRET must be at least 32 characters")
            String secret,
            @NotNull Duration ttl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Kafka(@NotBlank String samplesTopic, @NotBlank String deadLetterTopic, @Min(1) int partitions) {
    }

    public record Monitoring(
            @NotNull Duration staleAfter,
            @Min(1) int retentionDays,
            @NotNull Duration snapshotTtl,
            @Min(1) int defaultRateLimitPerMinute) {
    }

    public record Gemini(String apiKey, @NotBlank String model, @NotBlank String baseUrl,
                         @NotNull Duration timeout, @Min(1) int maxAttempts) {

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Webhook(@Min(1) int maxAttempts, @NotNull Duration timeout) {
    }
}
