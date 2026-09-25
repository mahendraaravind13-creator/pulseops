# Architecture decisions

Short records of the main decisions: the context, what was chosen, and what it costs. Newest last.

---

## 1. Use case: monitoring and incident management, AI as an assistant

**Context.** The first version claimed autonomous remediation ("fixes servers at 3 AM"). The remediation step was a `println`, and two of the six "AI steps" returned hardcoded values.

**Decision.** PulseOps collects metrics, evaluates alert rules on the server, and manages the incident lifecycle (open, deduplicate, acknowledge, resolve, auto-resolve). Gemini suggests a root cause and drafts a post-mortem. It never executes anything, and its output is labelled as a suggestion.

**Consequences.** Everything the product claims can be demonstrated end to end. The backend gets real business logic: windowed rule evaluation, a state machine, and deduplication under concurrency. Autonomous remediation is out of scope.

---

## 2. One Spring Boot application (modular monolith) instead of two services

**Context.** The ingestor and analyzer shared one database, duplicated entity classes by hand, and only one of them had security.

**Decision.** A single deployable with package-level modules (`ingest`, `evaluation`, `incident`, `ai`, `notification`, ...). Modules talk through service classes and Spring application events, not each other's repositories.

**Consequences.** One build, one security chain, one schema owner. The asynchronous boundary that matters (HTTP ingest → evaluation) is kept through Kafka inside the app. A module can later be extracted by replacing its events with Kafka topics. Running two copies of the same jar demonstrates horizontal scaling better than two different jars did.

---

## 3. Keep Kafka, with one topic

**Context.** Ingestion is bursty and must not lose data. Evaluation is slower (window queries, incident writes). Both app instances must share the evaluation work without processing a sample twice.

**Alternatives.** In-memory `@Async` queue: loses data on restart and cannot be shared between instances. Postgres outbox polled by a scheduler: durable and needs one less container, but re-implements a queue with polling and has no per-key ordering or consumer groups.

**Decision.** Topic `telemetry.samples` with 3 partitions, keyed by `tenantId:service`. Idempotent producer with `acks=all`. The HTTP handler waits up to 3 s for the broker acknowledgement, then returns 202 or 503. The consumer commits the offset after the DB transaction. Failures are retried with backoff, then sent to `telemetry.samples.dlq`.

**Consequences.** At-least-once delivery, so the consumer must be idempotent (decision 5). One more container to run.

---

## 4. Redis for three kinds of hot, shared, disposable state

**Decision.**
- **API-key cache** (cache-aside via `@Cacheable`): TTL 15 minutes, evicted explicitly on key rotation.
- **Rate limiter:** a fixed-window counter per tenant per minute, `INCR` + `EXPIRE` in one Lua script.
- **Latest snapshot:** a hash per service with a TTL, read by the dashboard.

**Failure policy.** Cache errors fall back to Postgres. The rate limiter fails open. Snapshot reads fall back to a `DISTINCT ON` query. Postgres is always the source of truth, so losing Redis costs latency, not correctness.

**Trade-off accepted.** A fixed window allows up to twice the limit across a minute boundary. This was observed in testing: 850 of 4,567 flood requests were accepted in a 20-second run that crossed a minute. A sliding window or token bucket would fix it, at the cost of more state.

---

## 5. Correctness in the database, not in Java memory

**Decision.**
- **Deduplication:** partial unique index `(service_id, rule_id) WHERE status <> 'RESOLVED'`, opened with `INSERT ... ON CONFLICT (...) WHERE status <> 'RESOLVED' DO NOTHING`. It is atomic across threads and instances, and throws no exception that would abort the transaction.
- **Idempotent samples:** `UNIQUE (service_id, recorded_at)` + `ON CONFLICT DO NOTHING`. The agent sends `recordedAt`, so its retries are recognised as duplicates.
- **Lost updates:** `@Version` optimistic locking on incident status changes. The client sends the version it saw, and a stale version returns 409.
- **Counters vs status:** breach counters are updated with atomic SQL increments on columns JPA never writes, so incoming telemetry does not bump the version and reject an engineer's click.

**Consequences.** No distributed locks and no in-memory state, so any number of instances behave correctly.

---

## 6. Side effects after commit, on bounded pools

**Decision.** Incident changes publish domain events, handled with `@TransactionalEventListener(AFTER_COMMIT)`.
- Notifications and webhook queue rows are written in a new transaction.
- Gemini calls run on a bounded pool (2–4 threads, queue 50). If the pool rejects a task, the analysis is marked FAILED with a retry button.
- A sweeper marks analyses stuck in PENDING (after a crash) as FAILED.

**Consequences.** A slow or failing external API never holds a DB connection or blocks the Kafka consumer. The gap: if the process dies between commit and the listener, the side effect is lost. Notifications accept that. AI work is recoverable through the sweeper and retry. A transactional outbox would close the gap fully and is the next step if it mattered.

---

## 7. Webhooks through a Postgres queue with `SKIP LOCKED`

**Decision.** Deliveries are rows in `webhook_deliveries`. A scheduled worker claims due rows with `SELECT ... FOR UPDATE SKIP LOCKED`, sends each one with a timeout, and on failure schedules a retry with exponential backoff (up to 5 attempts). Each request carries an `Idempotency-Key` header that stays the same across retries.

**Consequences.** Durable, retryable, and safe with several instances, with no extra infrastructure. Throughput is limited to what Postgres polling handles, which is ample for incident notifications.

---

## 8. Two authentication mechanisms, tenant always from the credential

**Decision.** Agents use API keys, stored as a SHA-256 hash plus a display prefix. Browsers use a 12-hour HS256 JWT with `tenantId` and `role` claims. There are two Spring Security filter chains, both stateless. Tenant-owned queries always take the tenant id from the principal, never from the URL or body. Owner-only actions (delete rule, rotate key, set webhook) use `@PreAuthorize`.

**Why SHA-256 and not BCrypt for API keys.** The keys are 192-bit random values, so brute force is not a concern. The lookup must be an indexed equality match on every request, which a salted slow hash cannot provide.

**Trade-off.** A JWT cannot be revoked before it expires. The 12-hour lifetime bounds that.

---

## 9. Flyway owns the schema

**Decision.** `ddl-auto: validate`. The schema lives in `V1__baseline.sql` and future `V2__...` files.

**Why.** Partial indexes, BRIN indexes, check constraints and `ON CONFLICT` targets cannot be expressed through JPA annotations. Migrations are versioned and reviewable, and run exactly once even with two instances starting together, because Flyway holds a lock.

---

## 10. JDBC for the hot path and PostgreSQL-specific SQL, JPA for the domain

**Decision.** JPA handles entities and state transitions: tenants, users, rules, incident status and events. `JdbcTemplate` handles upserts, idempotent inserts, window queries, time bucketing (`date_bin`), aggregates (`FILTER`, `generate_series`), and the filtered incident list.

**Why.** Each tool is used where it is clearest. The SQL that matters for performance and correctness is visible, not generated.

---

## 11. Java 25

**Decision.** Java 25, the current LTS release. Spring Boot 3.5, the build and all tests run on it, and the Docker images use `eclipse-temurin:25`.

**Consequences.** Moving to another LTS version means changing `<java.version>` in the pom and the two image tags in `backend/pulseops/Dockerfile`.

---

## What was left out, and why

| Left out | Reason |
|---|---|
| Microservices | No independent scaling need or separate teams. The shared data model would couple them anyway |
| Autonomous remediation / Kubernetes API | Cannot be built or demonstrated honestly here. The AI stays advisory |
| Chatbot | No product value. The old one had a cross-tenant default |
| Vector database / LangChain | AI context is structured (recent samples, similar past incidents), so SQL retrieval fits |
| Event sourcing / CQRS | The append-only `incident_events` table already gives an audit trail |
| Postgres row-level security | Needs session variables through the connection pool. Explicit tenant-scoped queries are testable |
| Resilience4j | One external dependency. Timeouts, bounded retries and visible failure states are enough |
| Prometheus / Grafana / tracing containers | `/actuator/prometheus` shows the metrics exist. One deployable, so a request id in logs suffices |
| WebSockets / SSE | Polling with TanStack Query is enough at this scale. SSE across two instances would need Redis pub/sub |
| Refresh tokens, OAuth | Scope. The trade-off is documented in decision 8 |
