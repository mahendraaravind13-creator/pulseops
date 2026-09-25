import { useQuery } from '@tanstack/react-query'
import { fetchServiceMetrics } from '../../api/services'
import { queryKeys } from '../../api/queryKeys'
import { ErrorState } from '../../components/ErrorState'
import { MetricChart } from '../../components/MetricChart'
import { Skeleton } from '../../components/Skeleton'
import { METRICS, TIME_RANGES } from '../../lib/constants'
import { thresholdLine, toChartData } from '../../lib/metrics'

const CONTEXT_SECONDS = 10 * 60

// The metrics endpoint only supports ranges ending "now", so pick the smallest
// range that still reaches back to a little before the incident opened.
function rangeCovering(openedAt) {
  const secondsNeeded = (Date.now() - new Date(openedAt).getTime()) / 1000 + CONTEXT_SECONDS
  return TIME_RANGES.find((range) => range.seconds >= secondsNeeded) ?? TIME_RANGES[TIME_RANGES.length - 1]
}

export function IncidentMetricChart({ incident }) {
  const metric = incident.condition?.metric ?? incident.metric
  const metricInfo = METRICS[metric]
  const range = rangeCovering(incident.openedAt)

  const metrics = useQuery({
    queryKey: queryKeys.serviceMetrics(incident.serviceId, range.value),
    queryFn: () => fetchServiceMetrics(incident.serviceId, range.value),
    refetchInterval: incident.status === 'RESOLVED' ? false : 15_000,
  })

  const thresholds = incident.condition
    ? [thresholdLine({ id: 'rule', ...incident.condition, severity: incident.severity, name: 'Threshold' })]
    : []
  const highlight = {
    from: new Date(incident.openedAt).getTime(),
    to: incident.resolvedAt ? new Date(incident.resolvedAt).getTime() : Date.now(),
  }

  return (
    <section className="card p-4" aria-labelledby="incident-metric-title">
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="incident-metric-title" className="text-sm font-semibold text-zinc-100">
          {metricInfo?.label ?? metric} on {incident.serviceName}
        </h2>
        <span className="text-xs text-subtle">Last {range.label} · shaded area is the incident window</span>
      </div>
      {metrics.isPending && <Skeleton className="h-60 w-full" />}
      {metrics.isError && <ErrorState error={metrics.error} onRetry={metrics.refetch} title="Could not load metrics" />}
      {metrics.data &&
        (metrics.data.points?.length ? (
          <MetricChart
            data={toChartData(metrics.data.points, metricInfo?.field, metrics.data.bucketSeconds)}
            unit={metricInfo?.unit}
            thresholds={thresholds}
            highlight={highlight}
            height={240}
            rangeSeconds={range.seconds}
          />
        ) : (
          <p className="py-16 text-center text-sm text-muted">
            No samples in the last {range.label}. Older data may have been removed by retention.
          </p>
        ))}
    </section>
  )
}
