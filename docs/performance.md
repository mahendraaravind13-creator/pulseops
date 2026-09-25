# Index experiment: measured, not assumed

Every index in `V1__baseline.sql` exists for a specific query. This page shows the effect on real query plans.

**Setup.** 2,000,000 samples: 50 services × 40,000 samples (one every 5 s, about 2.3 days each), PostgreSQL 16 in Docker Desktop on a laptop.
The script is [perf/explain.sql](perf/explain.sql). It runs inside one transaction and rolls back, so it leaves no data behind:

```bash
docker compose exec -T postgres psql -U pulseops -d pulseops < docs/perf/explain.sql
```

## Results

| Query | Used by | With index | Without index | Plan without index |
|---|---|---|---|---|
| **A.** Samples of one service in the last 5 minutes | rule evaluator, every sample | **0.29 ms** (bitmap index scan, 60 rows) | 69.8 ms | parallel seq scan, 2,001,324 rows filtered out |
| **B.** 1-hour chart, 30 s buckets | service detail page | **0.82 ms** (720 rows via index) | 81.2 ms | parallel seq scan, 2,000,664 rows filtered out |
| **D.** Retention batch: 5,000 rows older than 2 days | hourly retention job | **1.8 ms** (BRIN, 63 lossy blocks) | n/a | see "the BRIN lesson" below |

| Index | Size |
|---|---|
| `uq_samples_service_time` B-tree on `(service_id, recorded_at)` | 168 MB |
| `metric_samples_pkey` B-tree on `id` | 86 MB |
| `idx_samples_recorded_brin` BRIN on `recorded_at` | **24 kB** |

## What each result shows

**A and B: the composite index turns an O(table) scan into an O(result) lookup.**
Without it, PostgreSQL reads all 2 million rows (three parallel workers, about 19,000 buffer pages) to return 60. With it, it reads about 30 pages.
The gap grows with the table: the seq scan gets slower as data accumulates, while the index lookup stays proportional to the rows returned.
Column order matters. `(service_id, recorded_at)` supports "one service, a time range". The reverse order `(recorded_at, service_id)` would have to walk every service's rows inside the time range.

**The same index is also the idempotency guarantee.** It is declared as `UNIQUE (service_id, recorded_at)`, and the consumer inserts with `ON CONFLICT DO NOTHING`, so one structure serves both the reads and the duplicate check.

**D: BRIN is tiny, but only works when the physical order matches the column.**
A BRIN index stores just the min/max `recorded_at` for each block range, which is why it is 24 kB instead of hundreds of MB. It can skip whole block ranges only if rows are stored roughly in time order.

### The BRIN lesson (a real mistake made while measuring)

The first run of this experiment generated data service by service, with time running backwards inside each service. Every block range then contained both old and new timestamps, so the BRIN index could exclude nothing:

| Data layout | Retention batch time | Rows rechecked | Heap blocks read |
|---|---|---|---|
| Service-major, time descending (first run) | 296.8 ms | 1,715,985 | 16,084 (lossy) |
| Time-ordered, like real ingestion (current script) | 1.8 ms | 1,384 | 63 (lossy) |

Real agents report over time, so new rows are appended in roughly time order and BRIN fits. If the data were loaded in bulk out of order, or rows were heavily updated, a B-tree on `recorded_at` (or table partitioning by time) would be the better choice.

## Takeaways

- Name the query first, then the index. Each index in this schema maps to one access path.
- A composite index is read left to right: equality column first, then the range column.
- A unique constraint is an index, so it can do double duty: correctness and speed.
- BRIN trades precision for size and only works on physically correlated data. Here that means append-only, time-ordered telemetry.
- Measure with `EXPLAIN (ANALYZE, BUFFERS)`. Buffer counts show *why* a plan is slow, not just that it is.
