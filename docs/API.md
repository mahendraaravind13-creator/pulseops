# PulseOps REST API

Base path: `/api/v1`. All bodies are JSON. Timestamps are ISO-8601 UTC strings (`2026-09-25T10:15:30Z`).
Interactive docs: `/swagger-ui.html` when the backend is running.

## Authentication

| Caller | Credential | Used on |
|---|---|---|
| Browser (dashboard) | `Authorization: Bearer <jwt>` | every endpoint except `/auth/register`, `/auth/login`, `/ingest` |
| Agent | `X-API-Key: pk_...` | `POST /ingest` only |

The JWT carries `sub` (user id), `tenantId`, `role` (`OWNER` or `MEMBER`) and expires after 12 hours.
The tenant is always taken from the credential. No endpoint accepts a tenant id in the URL or body.

## Errors

Every error is an RFC 7807 problem document:

```json
{ "type": "about:blank", "title": "Conflict", "status": 409,
  "detail": "Incident was modified by someone else. Reload and try again.",
  "instance": "/api/v1/incidents/12/acknowledge",
  "errors": { "threshold": "must be greater than or equal to 0" } }
```

`errors` is present only for validation failures (400). Status codes used: 400, 401, 403, 404, 409, 429, 503.

## Pagination

List endpoints that page return:

```json
{ "content": [ ... ], "page": { "number": 0, "size": 20, "totalElements": 57, "totalPages": 3 } }
```

Query parameters: `page` (0-based), `size` (max 100), `sort` (e.g. `openedAt,desc`).

---

## Auth

### POST /auth/register
Creates a tenant, its owner user, a set of default alert rules, and an API key.
```json
{ "companyName": "Acme Corp", "fullName": "Asha Rao", "email": "asha@acme.io", "password": "min 8 chars" }
```
201:
```json
{ "token": "eyJ...", "user": { "id": 1, "email": "asha@acme.io", "fullName": "Asha Rao", "role": "OWNER" },
  "tenant": { "id": 1, "name": "Acme Corp", "slug": "acme-corp" },
  "apiKey": "pk_3f9c...full key, shown only once" }
```

### POST /auth/login
`{ "email": "...", "password": "..." }` → 200 `{ token, user, tenant }` (no apiKey). 401 on bad credentials.

### GET /auth/me
→ `{ user, tenant }`

---

## Ingest (agent)

### POST /ingest
Header `X-API-Key`. Body:
```json
{ "service": "checkout-api", "hostname": "web-1", "cpu": 42.5, "memory": 61.0,
  "disk": 55.2, "latencyMs": 120.0, "errorRate": 0.4, "recordedAt": "2026-09-25T10:15:30Z" }
```
`service`, `cpu`, `memory` required. Percentages are 0-100. `recordedAt` defaults to server time and may not be more than 60 s in the future.
Responses: 202 `{ "accepted": true }`, 401 bad key, 429 rate limited (with `Retry-After` seconds), 503 queue unavailable (agent should retry).

---

## Overview

### GET /overview
```json
{ "openIncidents": 3, "acknowledgedIncidents": 1, "criticalOpen": 2,
  "servicesTotal": 5, "servicesReporting": 4,
  "mttaSeconds": 312, "mttrSeconds": 1840,
  "incidentsPerDay": [ { "date": "2026-09-19", "count": 2 }, ... 7 entries, oldest first ] }
```
`mttaSeconds` / `mttrSeconds` cover incidents opened in the last 7 days and are `null` when there is no data.
"Open" counts `OPEN` only; `acknowledgedIncidents` counts `ACKNOWLEDGED`; `criticalOpen` counts OPEN or ACKNOWLEDGED with severity CRITICAL.

---

## Services

A service is registered automatically the first time the agent reports it.

### GET /services
```json
[ { "id": 4, "name": "checkout-api", "hostname": "web-1",
    "lastSeenAt": "2026-09-25T10:15:30Z",
    "status": "CRITICAL",
    "openIncidents": 1,
    "latest": { "cpu": 91.2, "memory": 63.0, "disk": 55.0, "latencyMs": 130.0, "errorRate": 0.2,
                "recordedAt": "2026-09-25T10:15:30Z" } } ]
```
`status`: `CRITICAL` or `WARNING` (worst severity among its OPEN/ACKNOWLEDGED incidents), `STALE` (no sample for 60 s), `HEALTHY`. `latest` may be `null`.

### GET /services/{id}
Same shape as one list element.

### GET /services/{id}/metrics?range=1h
`range` ∈ `15m, 1h, 6h, 24h, 7d`. Points are averaged per bucket, oldest first.
```json
{ "range": "1h", "bucketSeconds": 30,
  "points": [ { "t": "2026-09-25T09:15:00Z", "cpu": 40.1, "memory": 60.2, "disk": 55.0,
                "latencyMs": 110.0, "errorRate": 0.1 } ] }
```
Fields other than `t` may be `null`.

---

## Incidents

Status machine: `OPEN → ACKNOWLEDGED → RESOLVED`, `OPEN → RESOLVED`. Incidents are auto-resolved when the rule condition clears for its duration.
At most one non-resolved incident exists per (service, rule).

### GET /incidents
Query: `status` (comma list of `OPEN,ACKNOWLEDGED,RESOLVED`), `severity` (`CRITICAL|WARNING`), `serviceId`, `q` (matches title or service name), `from`, `to` (ISO, filter on `openedAt`), `page`, `size`, `sort` (`openedAt`, `severity`, `status`; default `openedAt,desc`).
Returns a page of:
```json
{ "id": 12, "title": "CPU above 85% for 60s on checkout-api", "status": "OPEN", "severity": "CRITICAL",
  "serviceId": 4, "serviceName": "checkout-api", "ruleId": 2, "ruleName": "High CPU", "metric": "CPU",
  "openedAt": "...", "acknowledgedAt": null, "resolvedAt": null, "lastBreachAt": "...",
  "peakValue": 96.4, "breachCount": 14, "version": 0 }
```

### GET /incidents/{id}
Summary fields plus:
```json
{ "condition": { "metric": "CPU", "operator": "GT", "threshold": 85.0, "durationSeconds": 60 },
  "resolutionNote": null,
  "acknowledgedBy": "Asha Rao", "resolvedBy": null, "autoResolved": false,
  "events": [ { "id": 1, "type": "OPENED", "message": "...", "actor": null, "createdAt": "..." } ],
  "analysis": { "status": "COMPLETED", "rootCause": "...", "confidence": 72,
                "suggestedActions": ["...", "..."], "model": "gemini-flash-lite-latest",
                "error": null, "postmortem": null, "postmortemStatus": null, "updatedAt": "..." } }
```
Event `type` ∈ `OPENED, ACKNOWLEDGED, RESOLVED, AUTO_RESOLVED, NOTE, ANALYSIS_COMPLETED, ANALYSIS_FAILED, POSTMORTEM_READY`.
`analysis.status` ∈ `PENDING, COMPLETED, FAILED, DISABLED` (DISABLED = no Gemini key configured). `postmortemStatus` ∈ `null, PENDING, COMPLETED, FAILED`.
`analysis` may be `null` for very old incidents.

### POST /incidents/{id}/acknowledge
`{ "version": 0 }` → 200 incident detail. 409 if the version is stale or the transition is not allowed.

### POST /incidents/{id}/resolve
`{ "version": 1, "note": "Rolled back deploy 4812" }` (note optional, max 2000) → 200 incident detail. 409 as above.

### POST /incidents/{id}/notes
`{ "message": "Looking into it" }` → 200 incident detail.

### POST /incidents/{id}/analysis/retry
→ 202 incident detail (analysis back to `PENDING`). 409 if analysis is already `PENDING`.

### POST /incidents/{id}/postmortem/retry
→ 202 incident detail. Only for `RESOLVED` incidents.

---

## Alert rules

### GET /rules
```json
[ { "id": 2, "name": "High CPU", "serviceId": null, "serviceName": null,
    "metric": "CPU", "operator": "GT", "threshold": 85.0, "durationSeconds": 60,
    "severity": "CRITICAL", "enabled": true, "activeIncidents": 1,
    "createdAt": "...", "updatedAt": "..." } ]
```
`serviceId: null` means the rule applies to every service.
`metric` ∈ `CPU, MEMORY, DISK, LATENCY_MS, ERROR_RATE`. `operator` ∈ `GT, LT`. `severity` ∈ `CRITICAL, WARNING`.
`durationSeconds` ∈ 0..3600 (0 = fire on a single sample).

### POST /rules → 201 rule
`{ "name", "serviceId" (nullable), "metric", "operator", "threshold", "durationSeconds", "severity", "enabled" }`

### PUT /rules/{id} → 200 rule (same body)
### PATCH /rules/{id}/enabled → 200 rule. Body `{ "enabled": false }`
### DELETE /rules/{id} → 204. Owner only. Active incidents of the rule are resolved first.

---

## Notifications

### GET /notifications?limit=20
```json
{ "unreadCount": 2,
  "items": [ { "id": 9, "type": "INCIDENT_OPENED", "title": "...", "body": "...",
               "incidentId": 12, "read": false, "createdAt": "..." } ] }
```
`type` ∈ `INCIDENT_OPENED, INCIDENT_RESOLVED`.

### POST /notifications/{id}/read → 204
### POST /notifications/read-all → 204

---

## Settings

### GET /settings
```json
{ "tenant": { "id": 1, "name": "Acme Corp", "slug": "acme-corp", "createdAt": "..." },
  "apiKeyPrefix": "pk_3f9c1a", "rateLimitPerMinute": 600, "retentionDays": 7,
  "webhookUrl": null }
```

### POST /settings/api-key/rotate → 200 `{ "apiKey": "pk_...", "apiKeyPrefix": "pk_..." }` (owner only; old key stops working immediately)
### PUT /settings/webhook → 200 settings. Body `{ "webhookUrl": "https://..." | null }` (owner only)
### GET /settings/webhook/deliveries?limit=20
```json
[ { "id": 3, "event": "INCIDENT_OPENED", "title": "...", "status": "DELIVERED",
    "attempts": 1, "responseCode": 200, "lastError": null,
    "createdAt": "...", "deliveredAt": "..." } ]
```
`status` ∈ `PENDING, DELIVERED, FAILED`.
