-- Index experiment for docs/performance.md. Reproduce with:
--   docker compose exec -T postgres psql -U pulseops -d pulseops < docs/perf/explain.sql
-- Everything runs in one transaction and is ROLLED BACK at the end: no data is left behind.
\timing off
\pset pager off
BEGIN;

-- 1. A throwaway tenant with 50 services x 40,000 samples (one every 5s, ~2.3 days each) = 2,000,000 rows.
INSERT INTO tenants (name, slug, api_key_hash, api_key_prefix) VALUES ('perf', 'perf-test', repeat('0', 64), 'pk_perf');
INSERT INTO services (tenant_id, name, last_seen_at)
SELECT currval('tenants_id_seq'), 'svc-' || g, now() FROM generate_series(1, 50) g;

-- ORDER BY n DESC inserts oldest first, interleaving services, like real agents reporting over time.
-- That physical time order is what makes the BRIN index effective (see docs/performance.md, section D).
INSERT INTO metric_samples (service_id, recorded_at, cpu, memory, disk)
SELECT s.id, now() - (n * interval '5 seconds'), random() * 100, random() * 100, random() * 100
FROM services s, generate_series(1, 40000) n
WHERE s.tenant_id = currval('tenants_id_seq')
ORDER BY n DESC, s.id;

ANALYZE metric_samples;
SELECT count(*) AS total_samples FROM metric_samples;

-- The service we query: one of the 50.
SELECT id AS target_service FROM services WHERE tenant_id = currval('tenants_id_seq') ORDER BY id LIMIT 1 \gset

\echo '=== A. Rule window query WITH the (service_id, recorded_at) index ==='
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT recorded_at, cpu, memory FROM metric_samples
WHERE service_id = :target_service AND recorded_at BETWEEN now() - interval '5 minutes' AND now()
ORDER BY recorded_at;

\echo '=== B. Chart query (1h, 30s buckets) WITH the index ==='
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT date_bin(interval '30 seconds', recorded_at, timestamptz '2000-01-01') AS t, avg(cpu)
FROM metric_samples
WHERE service_id = :target_service AND recorded_at >= now() - interval '1 hour'
GROUP BY 1 ORDER BY 1;

\echo '=== C. Index sizes: B-tree (service_id, recorded_at) vs BRIN (recorded_at) ==='
SELECT indexrelname AS index, pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes WHERE relname = 'metric_samples' ORDER BY pg_relation_size(indexrelid) DESC;

\echo '=== D. Retention batch (rows older than 2 days) WITH the BRIN index ==='
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT id FROM metric_samples WHERE recorded_at < now() - interval '2 days' LIMIT 5000;

-- Now remove the indexes (inside the transaction; the ROLLBACK restores them).
ALTER TABLE metric_samples DROP CONSTRAINT uq_samples_service_time;
DROP INDEX idx_samples_recorded_brin;

\echo '=== A2. Rule window query WITHOUT the index ==='
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT recorded_at, cpu, memory FROM metric_samples
WHERE service_id = :target_service AND recorded_at BETWEEN now() - interval '5 minutes' AND now()
ORDER BY recorded_at;

\echo '=== B2. Chart query WITHOUT the index ==='
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT date_bin(interval '30 seconds', recorded_at, timestamptz '2000-01-01') AS t, avg(cpu)
FROM metric_samples
WHERE service_id = :target_service AND recorded_at >= now() - interval '1 hour'
GROUP BY 1 ORDER BY 1;

ROLLBACK;
