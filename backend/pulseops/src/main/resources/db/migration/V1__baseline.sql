-- PulseOps baseline schema.
-- Every tenant-owned table carries tenant_id (directly or through its parent) and every query filters on it.

CREATE TABLE tenants (
    id                     BIGSERIAL PRIMARY KEY,
    name                   VARCHAR(120) NOT NULL,
    slug                   VARCHAR(80)  NOT NULL UNIQUE,
    -- Only a SHA-256 hash of the API key is stored. The prefix is kept so the UI can show which key is active.
    api_key_hash           VARCHAR(64)  NOT NULL UNIQUE,
    api_key_prefix         VARCHAR(16)  NOT NULL,
    rate_limit_per_minute  INTEGER      NOT NULL DEFAULT 600,
    webhook_url            VARCHAR(500),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES tenants (id),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    role           VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_tenant ON users (tenant_id);

-- A monitored service is registered automatically on its first sample.
CREATE TABLE services (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenants (id),
    name          VARCHAR(100) NOT NULL,
    hostname      VARCHAR(255),
    last_seen_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_services_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE metric_samples (
    id           BIGSERIAL PRIMARY KEY,
    service_id   BIGINT           NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    recorded_at  TIMESTAMPTZ      NOT NULL,
    cpu          DOUBLE PRECISION NOT NULL,
    memory       DOUBLE PRECISION NOT NULL,
    disk         DOUBLE PRECISION,
    latency_ms   DOUBLE PRECISION,
    error_rate   DOUBLE PRECISION,
    -- Two jobs in one constraint:
    --  1. Idempotency: a redelivered Kafka message or an agent retry with the same recordedAt is ignored
    --     (INSERT ... ON CONFLICT DO NOTHING).
    --  2. Its B-tree index (service_id, recorded_at) serves every window and chart query
    --     ("samples of service X between T1 and T2"). B-trees scan in both directions, so no extra DESC index.
    CONSTRAINT uq_samples_service_time UNIQUE (service_id, recorded_at)
);
-- BRIN index for the retention job ("delete everything older than 7 days").
-- Samples arrive roughly in time order, so a BRIN index is a few KB instead of a full B-tree over every row.
CREATE INDEX idx_samples_recorded_brin ON metric_samples USING BRIN (recorded_at);

CREATE TABLE alert_rules (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT           NOT NULL REFERENCES tenants (id),
    name              VARCHAR(120)     NOT NULL,
    service_id        BIGINT REFERENCES services (id) ON DELETE CASCADE, -- NULL = applies to every service
    metric            VARCHAR(16)      NOT NULL CHECK (metric IN ('CPU', 'MEMORY', 'DISK', 'LATENCY_MS', 'ERROR_RATE')),
    operator          VARCHAR(4)       NOT NULL CHECK (operator IN ('GT', 'LT')),
    threshold         DOUBLE PRECISION NOT NULL,
    duration_seconds  INTEGER          NOT NULL CHECK (duration_seconds BETWEEN 0 AND 3600),
    severity          VARCHAR(16)      NOT NULL CHECK (severity IN ('CRITICAL', 'WARNING')),
    enabled           BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_rules_tenant ON alert_rules (tenant_id);

CREATE TABLE incidents (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT           NOT NULL REFERENCES tenants (id),
    service_id        BIGINT           NOT NULL REFERENCES services (id) ON DELETE CASCADE,
    rule_id           BIGINT REFERENCES alert_rules (id) ON DELETE SET NULL,
    -- Snapshot of the rule at the time the incident opened, so editing or deleting a rule never rewrites history.
    rule_name         VARCHAR(120)     NOT NULL,
    metric            VARCHAR(16)      NOT NULL,
    operator          VARCHAR(4)       NOT NULL,
    threshold         DOUBLE PRECISION NOT NULL,
    duration_seconds  INTEGER          NOT NULL,
    title             VARCHAR(200)     NOT NULL,
    severity          VARCHAR(16)      NOT NULL,
    status            VARCHAR(16)      NOT NULL CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    opened_at         TIMESTAMPTZ      NOT NULL,
    acknowledged_at   TIMESTAMPTZ,
    acknowledged_by   BIGINT REFERENCES users (id),
    resolved_at       TIMESTAMPTZ,
    resolved_by       BIGINT REFERENCES users (id),   -- NULL on a resolved incident = auto-resolved
    resolution_note   TEXT,
    -- Telemetry counters, updated with atomic SQL increments on every breaching sample.
    last_breach_at    TIMESTAMPTZ      NOT NULL,
    peak_value        DOUBLE PRECISION NOT NULL,
    breach_count      INTEGER          NOT NULL DEFAULT 1,
    -- Optimistic lock for status changes (acknowledge / resolve / auto-resolve).
    version           BIGINT           NOT NULL DEFAULT 0
);

-- THE deduplication rule: at most one unresolved incident per (service, rule).
-- It is a partial index, so resolved incidents do not block a new one when the problem comes back.
-- Opening uses INSERT ... ON CONFLICT (service_id, rule_id) WHERE status <> 'RESOLVED' DO NOTHING,
-- which is atomic even when two app instances evaluate samples at the same moment.
CREATE UNIQUE INDEX uq_incidents_active ON incidents (service_id, rule_id) WHERE status <> 'RESOLVED';

-- Incident list page: WHERE tenant_id = ? [AND status IN (...)] ORDER BY opened_at DESC.
CREATE INDEX idx_incidents_tenant_opened ON incidents (tenant_id, opened_at DESC);
CREATE INDEX idx_incidents_tenant_status ON incidents (tenant_id, status, opened_at DESC);

CREATE TABLE incident_events (
    id             BIGSERIAL PRIMARY KEY,
    incident_id    BIGINT      NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    type           VARCHAR(32) NOT NULL,
    message        TEXT        NOT NULL,
    actor_user_id  BIGINT REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_incident_events_incident ON incident_events (incident_id, created_at);

CREATE TABLE ai_analyses (
    id                 BIGSERIAL PRIMARY KEY,
    incident_id        BIGINT      NOT NULL UNIQUE REFERENCES incidents (id) ON DELETE CASCADE,
    status             VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'DISABLED')),
    root_cause         TEXT,
    confidence         INTEGER,
    suggested_actions  JSONB,
    model              VARCHAR(64),
    error              TEXT,
    attempts           INTEGER     NOT NULL DEFAULT 0,
    postmortem         TEXT,
    postmortem_status  VARCHAR(16) CHECK (postmortem_status IN ('PENDING', 'COMPLETED', 'FAILED')),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenants (id),
    incident_id  BIGINT REFERENCES incidents (id) ON DELETE CASCADE,
    type         VARCHAR(32)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_tenant ON notifications (tenant_id, created_at DESC);

-- Outbound webhook queue. Workers claim rows with SELECT ... FOR UPDATE SKIP LOCKED,
-- so two app instances never deliver the same row twice at the same time.
CREATE TABLE webhook_deliveries (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES tenants (id),
    notification_id  BIGINT REFERENCES notifications (id) ON DELETE SET NULL,
    event            VARCHAR(32)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    url              VARCHAR(500) NOT NULL,
    payload          TEXT         NOT NULL,
    status           VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts         INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    response_code    INTEGER,
    last_error       TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    delivered_at     TIMESTAMPTZ
);
CREATE INDEX idx_webhooks_pending ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhooks_tenant ON webhook_deliveries (tenant_id, created_at DESC);
