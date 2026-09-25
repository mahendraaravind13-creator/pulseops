package com.pulseops.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.config.PulseOpsProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GeminiClient against a stub HTTP server (the JDK's built-in one, so no extra test dependency):
 * success, retry on 429, no retry on 400, malformed response, and read timeout.
 */
class GeminiClientTest {

    private static final String OK_BODY = """
            {"candidates":[{"content":{"parts":[{"text":"{\\"root_cause\\":\\"x\\"}"}]}}]}
            """;

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> lastApiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();

    record Reply(int status, String body, long delayMs) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            lastApiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            lastQuery.set(exchange.getRequestURI().getQuery());
            exchange.getRequestBody().readAllBytes();
            Reply reply = replies.isEmpty() ? new Reply(500, "{}", 0) : replies.poll();
            sleep(reply.delayMs());
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(reply.status(), body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException ignored) {
                // client gave up (timeout test)
            }
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GeminiClient client(Duration timeout, int maxAttempts) {
        var gemini = new PulseOpsProperties.Gemini("test-key", "test-model",
                "http://127.0.0.1:" + server.getAddress().getPort(), timeout, maxAttempts);
        var props = new PulseOpsProperties(null, null, null, null, gemini, null);
        return new GeminiClient(new ObjectMapper(), props, new SimpleMeterRegistry());
    }

    @Test
    void returnsTheModelTextAndSendsKeyInHeaderNotUrl() {
        replies.add(new Reply(200, OK_BODY, 0));
        GeminiClient.Reply reply = client(Duration.ofSeconds(2), 3).generate("prompt", true);
        assertThat(reply.text()).isEqualTo("{\"root_cause\":\"x\"}");
        assertThat(reply.attempts()).isEqualTo(1);
        assertThat(lastApiKeyHeader.get()).isEqualTo("test-key");
        assertThat(lastQuery.get()).isNull();
    }

    @Test
    void retriesRateLimitAndServerErrorsThenSucceeds() {
        replies.addAll(List.of(new Reply(429, "{}", 0), new Reply(503, "{}", 0), new Reply(200, OK_BODY, 0)));
        GeminiClient.Reply reply = client(Duration.ofSeconds(2), 3).generate("prompt", true);
        assertThat(reply.attempts()).isEqualTo(3);
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        replies.addAll(List.of(new Reply(400, "{\"error\":\"bad key\"}", 0), new Reply(200, OK_BODY, 0)));
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), 3).generate("prompt", true))
                .isInstanceOf(GeminiClient.GeminiException.class)
                .hasMessageContaining("400");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void malformedResponseFailsWithoutRetry() {
        replies.add(new Reply(200, "{\"candidates\":[]}", 0));
        assertThatThrownBy(() -> client(Duration.ofSeconds(2), 3).generate("prompt", true))
                .isInstanceOf(GeminiClient.GeminiException.class)
                .hasMessageContaining("no text");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void readTimeoutIsRetriedAndThenReported() {
        replies.addAll(List.of(new Reply(200, OK_BODY, 1500), new Reply(200, OK_BODY, 1500)));
        long started = System.nanoTime();
        assertThatThrownBy(() -> client(Duration.ofMillis(300), 2).generate("prompt", true))
                .isInstanceOf(GeminiClient.GeminiException.class)
                .hasMessageContaining("after 2 attempts");
        assertThat(requests.get()).isEqualTo(2);
        // Two 300ms timeouts plus ~1s backoff: the caller is never held for the server's full 1.5s delays.
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
