package com.pulseops;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulseops.auth.JwtService;
import com.pulseops.auth.User;
import com.pulseops.auth.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests against real PostgreSQL, Kafka and Redis containers: the HTTP API, the Kafka pipeline, the
 * rule evaluator, incident deduplication, tenant isolation, optimistic locking and rate limiting.
 * Requires Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PulseOpsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("pulseops.jwt.secret", () -> "integration-test-secret-0123456789abcdef");
        registry.add("pulseops.gemini.api-key", () -> ""); // AI disabled: analyses are created as DISABLED
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JwtService jwtService;

    @Autowired
    UserRepository userRepository;

    private Account acme;
    private Account globex;

    record Account(String token, String apiKey, long tenantId) {
    }

    @BeforeAll
    void registerTenants() {
        acme = register("Acme");
        globex = register("Globex");
    }

    @Test
    void sustainedBreachOpensExactlyOneIncidentAndRecoveryAutoResolvesIt() {
        Instant base = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);

        // 13 samples of 95% CPU over 120s. The default rule is "CPU > 85 for 60s". With the evaluator's coverage
        // tolerance (15s for a 60s window) the window counts as covered from base+50 on, so the 8 samples
        // base+50 .. base+120 are each evaluated as BREACHING. They must produce ONE incident with 8 breaches.
        for (int s = 0; s <= 120; s += 10) {
            assertThat(ingest(acme, "checkout", 95, base.plusSeconds(s)).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
        long id = awaitSingleIncident(acme, "checkout", n -> true).path("id").asLong();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(get(acme, "/api/v1/incidents/" + id).path("breachCount").asInt()).isEqualTo(8));
        JsonNode incident = get(acme, "/api/v1/incidents/" + id);
        assertThat(incident.path("status").asText()).isEqualTo("OPEN");
        assertThat(incident.path("severity").asText()).isEqualTo("CRITICAL");

        // Sending the same samples again (Kafka redelivery / agent retry) changes nothing: idempotent inserts.
        for (int s = 0; s <= 120; s += 10) {
            ingest(acme, "checkout", 95, base.plusSeconds(s));
        }
        ingest(acme, "marker", 10, base); // a sample on another partition key, just to let the consumer catch up
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(get(acme, "/api/v1/incidents/" + id).path("breachCount").asInt()).isEqualTo(8));

        // CPU back to normal for a full minute: the incident resolves itself.
        for (int s = 130; s <= 200; s += 10) {
            ingest(acme, "checkout", 20, base.plusSeconds(s));
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            JsonNode detail = get(acme, "/api/v1/incidents/" + id);
            assertThat(detail.path("status").asText()).isEqualTo("RESOLVED");
            assertThat(detail.path("autoResolved").asBoolean()).isTrue();
            assertThat(detail.path("analysis").path("status").asText()).isEqualTo("DISABLED");
            assertThat(detail.path("events").toString()).contains("OPENED", "AUTO_RESOLVED");
        });

        // A notification was created for the tenant.
        JsonNode notifications = get(acme, "/api/v1/notifications");
        assertThat(notifications.path("items").toString()).contains("INCIDENT_OPENED", "INCIDENT_RESOLVED");

        // Health view: service known, latest value present (from the Redis snapshot).
        JsonNode services = getArray(acme, "/api/v1/services");
        JsonNode checkout = find(services, "name", "checkout");
        assertThat(checkout.path("latest").path("cpu").asDouble()).isEqualTo(20.0);
    }

    @Test
    void tenantsCannotSeeEachOthersData() {
        Instant base = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        for (int s = 0; s <= 70; s += 10) {
            ingest(acme, "billing", 99, base.plusSeconds(s));
        }
        JsonNode incident = awaitSingleIncident(acme, "billing", n -> true);
        long id = incident.path("id").asLong();

        ResponseEntity<String> otherTenant = exchange(globex, HttpMethod.GET, "/api/v1/incidents/" + id, null);
        assertThat(otherTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(globex, "/api/v1/incidents?q=billing").path("content")).isEmpty();
        assertThat(getArray(globex, "/api/v1/services").toString()).doesNotContain("billing");
    }

    @Test
    void acknowledgeUsesOptimisticVersionAndStateMachine() {
        Instant base = Instant.now().minus(7, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        for (int s = 0; s <= 70; s += 10) {
            ingest(acme, "search", 97, base.plusSeconds(s));
        }
        JsonNode incident = awaitSingleIncident(acme, "search", n -> true);
        long id = incident.path("id").asLong();
        long version = incident.path("version").asLong();

        ResponseEntity<String> stale = exchange(acme, HttpMethod.POST, "/api/v1/incidents/" + id + "/acknowledge",
                Map.of("version", version + 5));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> ok = exchange(acme, HttpMethod.POST, "/api/v1/incidents/" + id + "/acknowledge",
                Map.of("version", version));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).contains("\"status\":\"ACKNOWLEDGED\"");

        // Acknowledging again is an invalid transition.
        ResponseEntity<String> again = exchange(acme, HttpMethod.POST, "/api/v1/incidents/" + id + "/acknowledge",
                Map.of("version", version + 1));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> resolved = exchange(acme, HttpMethod.POST, "/api/v1/incidents/" + id + "/resolve",
                Map.of("version", version + 1, "note", "Restarted the indexer"));
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resolved.getBody()).contains("Restarted the indexer").contains("\"autoResolved\":false");
    }

    @Test
    void ingestRequiresAValidApiKeyAndDashboardRequiresAToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "pk_not_a_real_key");
        ResponseEntity<String> badKey = rest.exchange("/api/v1/ingest", HttpMethod.POST,
                new HttpEntity<>(Map.of("service", "x", "cpu", 1, "memory", 1), headers), String.class);
        assertThat(badKey.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> noToken = rest.getForEntity("/api/v1/incidents", String.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(noToken.getHeaders().getContentType().toString()).contains("problem+json");
    }

    @Test
    void invalidSamplesAndRulesAreRejectedWithFieldErrors() {
        ResponseEntity<String> badSample = ingest(acme, "bad service name!", 150, Instant.now());
        assertThat(badSample.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badSample.getBody()).contains("\"errors\"").contains("cpu").contains("service");

        ResponseEntity<String> badRule = exchange(acme, HttpMethod.POST, "/api/v1/rules", Map.of(
                "name", "Impossible", "metric", "CPU", "operator", "GT", "threshold", 150,
                "durationSeconds", 60, "severity", "WARNING"));
        assertThat(badRule.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rateLimitReturns429WithRetryAfter() {
        Account small = register("Tiny");
        jdbc.update("UPDATE tenants SET rate_limit_per_minute = 3 WHERE id = ?", small.tenantId());
        int accepted = 0;
        ResponseEntity<String> last = null;
        for (int i = 0; i < 5; i++) {
            last = ingest(small, "api", 10, Instant.now().minusSeconds(i));
            if (last.getStatusCode() == HttpStatus.ACCEPTED) accepted++;
        }
        // Normally exactly 3 are accepted; 3-5 if the loop straddles a minute boundary (fixed-window behaviour).
        assertThat(accepted).isBetween(3, 5);
        if (accepted == 3) {
            assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(last.getHeaders().getFirst("Retry-After")).isNotNull();
        }
    }

    @Test
    void membersCannotPerformOwnerOnlyActions() {
        Account owner = register("Roles");
        Long memberId = jdbc.queryForObject("""
                INSERT INTO users (tenant_id, email, password_hash, full_name, role)
                VALUES (?, ?, 'not-used', 'Team Member', 'MEMBER') RETURNING id
                """, Long.class, owner.tenantId(), "member-" + UUID.randomUUID() + "@example.com");
        User member = userRepository.findById(memberId).orElseThrow();
        Account asMember = new Account(jwtService.issue(member), owner.apiKey(), owner.tenantId());

        long ruleId = getArrayFirstId(owner, "/api/v1/rules");
        assertThat(exchange(asMember, HttpMethod.DELETE, "/api/v1/rules/" + ruleId, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(asMember, HttpMethod.POST, "/api/v1/settings/api-key/rotate", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // Members can still read and work incidents / rules.
        assertThat(exchange(asMember, HttpMethod.GET, "/api/v1/rules", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        // The owner can delete.
        assertThat(exchange(owner, HttpMethod.DELETE, "/api/v1/rules/" + ruleId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void ownerCanRotateApiKeyAndOldKeyStopsWorkingImmediately() {
        Account account = register("Rotator");
        assertThat(ingest(account, "svc", 10, Instant.now()).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED); // now cached

        JsonNode rotated = json(exchange(account, HttpMethod.POST, "/api/v1/settings/api-key/rotate", null));
        String newKey = rotated.path("apiKey").asText();

        assertThat(ingest(account, "svc", 10, Instant.now().minusSeconds(1)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Account withNewKey = new Account(account.token(), newKey, account.tenantId());
        assertThat(ingest(withNewKey, "svc", 10, Instant.now().minusSeconds(2)).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    // ------------------------------------------------------------------ helpers

    private Account register(String company) {
        String email = company.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<String> res = rest.postForEntity("/api/v1/auth/register", Map.of(
                "companyName", company, "fullName", company + " Owner", "email", email, "password", "correct-horse"),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = json(res);
        return new Account(body.path("token").asText(), body.path("apiKey").asText(), body.path("tenant").path("id").asLong());
    }

    private ResponseEntity<String> ingest(Account account, String service, double cpu, Instant at) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", account.apiKey());
        Map<String, Object> body = Map.of("service", service, "cpu", cpu, "memory", 40, "disk", 50,
                "recordedAt", at.toString());
        return rest.exchange("/api/v1/ingest", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode awaitSingleIncident(Account account, String service, java.util.function.Predicate<JsonNode> condition) {
        JsonNode[] holder = new JsonNode[1];
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            JsonNode page = get(account, "/api/v1/incidents?q=" + service);
            JsonNode matching = page.path("content");
            assertThat(matching).hasSize(1);
            assertThat(condition.test(matching.get(0))).isTrue();
            holder[0] = matching.get(0);
        });
        return holder[0];
    }

    private ResponseEntity<String> exchange(Account account, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode get(Account account, String path) {
        ResponseEntity<String> res = exchange(account, HttpMethod.GET, path, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json(res);
    }

    private JsonNode getArray(Account account, String path) {
        return get(account, path);
    }

    private long getArrayFirstId(Account account, String path) {
        return get(account, path).get(0).path("id").asLong();
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode n : array) {
            if (value.equals(n.path(field).asText())) return n;
        }
        throw new AssertionError("No element with " + field + "=" + value + " in " + array);
    }

    private static JsonNode json(ResponseEntity<String> res) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(res.getBody());
        } catch (Exception e) {
            throw new AssertionError("Not JSON: " + res.getBody(), e);
        }
    }
}
