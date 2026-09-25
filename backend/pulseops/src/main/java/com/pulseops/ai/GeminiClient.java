package com.pulseops.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.config.PulseOpsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Minimal Gemini REST client.
 *
 * <p>Failure handling:
 * <ul>
 *   <li>Timeouts: 5s to connect, {@code pulseops.gemini.timeout} to read. A hung call cannot hold a thread forever.</li>
 *   <li>Retries: only for errors that can succeed on a second try (network errors, 429 rate limiting, 5xx), with
 *       exponential backoff plus jitter so many retries do not arrive in lockstep. 4xx errors such as a bad API key
 *       fail immediately, because retrying cannot fix them.</li>
 *   <li>The key travels in the {@code x-goog-api-key} header, not the URL, so it never lands in access logs.</li>
 * </ul>
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient http;
    private final ObjectMapper mapper;
    private final PulseOpsProperties.Gemini config;
    private final MeterRegistry meters;

    public GeminiClient(ObjectMapper mapper, PulseOpsProperties properties, MeterRegistry meters) {
        this.mapper = mapper;
        this.config = properties.gemini();
        this.meters = meters;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(config.timeout());
        this.http = RestClient.builder().baseUrl(config.baseUrl()).requestFactory(factory).build();
    }

    public record Reply(String text, int attempts) {
    }

    public static class GeminiException extends RuntimeException {
        private final int attempts;

        GeminiException(String message, int attempts, Throwable cause) {
            super(message, cause);
            this.attempts = attempts;
        }

        public int attempts() {
            return attempts;
        }
    }

    public String model() {
        return config.model();
    }

    /** @param json true to ask Gemini for a JSON document (structured output) instead of free text */
    public Reply generate(String prompt, boolean json) {
        Map<String, Object> generationConfig = json
                ? Map.of("temperature", 0.2, "responseMimeType", "application/json")
                : Map.of("temperature", 0.4);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", generationConfig);

        int maxAttempts = config.maxAttempts();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String raw = Timer.builder("pulseops.ai.request").tag("model", config.model()).register(meters)
                        .recordCallable(() -> http.post()
                                .uri("/models/{model}:generateContent", config.model())
                                .header("x-goog-api-key", config.apiKey())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body)
                                .retrieve()
                                .body(String.class));
                return new Reply(extractText(raw), attempt);
            } catch (RestClientResponseException e) {
                last = e;
                if (!isRetryable(e.getStatusCode())) {
                    throw new GeminiException("Gemini rejected the request (HTTP " + e.getStatusCode().value() + ")", attempt, e);
                }
                log.warn("Gemini HTTP {} on attempt {}/{}", e.getStatusCode().value(), attempt, maxAttempts);
            } catch (ResourceAccessException e) {
                last = e;
                log.warn("Gemini network error on attempt {}/{}: {}", attempt, maxAttempts, e.getMessage());
            } catch (GeminiException e) {
                throw e;
            } catch (Exception e) {
                throw new GeminiException("Unexpected Gemini error: " + e.getMessage(), attempt, e);
            }
            if (attempt < maxAttempts) {
                sleep(backoff(attempt));
            }
        }
        throw new GeminiException("Gemini unavailable after " + maxAttempts + " attempts", maxAttempts, last);
    }

    private String extractText(String raw) {
        try {
            JsonNode text = mapper.readTree(raw).path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (text.isMissingNode() || text.asText().isBlank()) {
                throw new GeminiException("Gemini returned no text (possibly blocked by safety filters)", 1, null);
            }
            return text.asText();
        } catch (GeminiException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiException("Could not parse Gemini response", 1, e);
        }
    }

    private static boolean isRetryable(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    /** 1s, 2s, 4s ... plus up to 250ms of random jitter. */
    static Duration backoff(int attempt) {
        long base = 1000L << (attempt - 1);
        return Duration.ofMillis(base + ThreadLocalRandom.current().nextLong(250));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeminiException("Interrupted while waiting to retry", 0, e);
        }
    }
}
