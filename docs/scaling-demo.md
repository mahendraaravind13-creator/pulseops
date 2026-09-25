# Scaling and failover demo

Shows that the app is stateless: two instances share HTTP traffic and Kafka partitions, and either can die without losing requests or telemetry.
Output below was captured from a real run on 2026-09-25.

## 1. Start the stack and look at the two instances

```bash
docker compose up -d --build
docker compose ps
```

```
pulseops-stack-app-1        Up (healthy)
pulseops-stack-app-2        Up (healthy)
pulseops-stack-kafka-1      Up (healthy)
pulseops-stack-postgres-1   Up (healthy)
pulseops-stack-redis-1      Up (healthy)
pulseops-stack-web-1        Up
```

## 2. See how Kafka split the work

The topic `telemetry.samples` has 3 partitions. Each instance joins the consumer group `pulseops-evaluator`, and Kafka assigns every partition to exactly one of them.

```bash
docker compose logs app | grep "partitions assigned"
```

```
app-1  partitions assigned: [telemetry.samples-0]
app-1  partitions assigned: [telemetry.samples-2]
app-2  partitions assigned: [telemetry.samples-1]
```

The record key is `tenantId:service`, so all samples of one service land in one partition, and so on one instance, in order.

Flyway also shows the instances coordinating: only one of them applied the migration, while the other waited on Flyway's lock table.

```
app-2  Migrating schema "public" to version "1 - baseline"
app-2  Successfully applied 1 migration to schema "public"
```

## 3. Kill one instance while traffic flows

```bash
docker stop pulseops-stack-app-2
for i in $(seq 1 30); do curl -s -o /dev/null -w '%{http_code}\n' localhost:8088/api/v1/overview -H "Authorization: Bearer $TOKEN"; done | sort | uniq -c
```

```
     30 200
```

All 30 requests succeeded. nginx's `proxy_next_upstream error timeout http_502 http_503` retries a failed attempt on the other replica, so clients never see the dead instance.

Kafka notices that app-2's consumer left the group, rebalances, and hands its partition to the survivor:

```bash
docker logs pulseops-stack-app-1 --since 60s | grep "partitions assigned"
```

```
partitions assigned: [telemetry.samples-2]
partitions assigned: [telemetry.samples-1]    <- previously app-2's
```

Processing resumes from the last committed offset of partition 1. No sample is lost. Any sample that was in flight is redelivered, and the idempotent insert ignores it if it was already stored.

## 4. Bring it back

```bash
docker start pulseops-stack-app-2
```

Another rebalance spreads the partitions over both instances again.

## Why this works

| Would break with 2 instances | How PulseOps avoids it |
|---|---|
| HTTP sessions stored in one JVM | Stateless JWT; any instance can verify it |
| An in-memory cooldown map (the old design) | "One active incident" is a partial unique index in Postgres |
| Two instances opening the same incident | `INSERT ... ON CONFLICT ... DO NOTHING` on that index |
| Per-instance rate-limit counters | Counters live in Redis, shared by both |
| Both instances sending the same webhook | `SELECT ... FOR UPDATE SKIP LOCKED` claims each delivery once |
| Scheduled jobs running twice | Retention and the stuck-analysis sweeper are idempotent |
| Local caches going stale | The API-key cache is in Redis and evicted on rotation |
