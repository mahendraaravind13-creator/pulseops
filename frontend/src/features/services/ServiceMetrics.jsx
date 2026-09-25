import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { fetchIncidents } from '../../api/incidents'
import { fetchServiceMetrics } from '../../api/services'
import { queryKeys } from '../../api/queryKeys'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { MetricChart } from '../../components/MetricChart'
import { Skeleton } from '../../components/Skeleton'
import { METRICS, TIME_RANGES } from '../../lib/constants'
import { hasAnyValue, thresholdsForMetric, toChartData } from '../../lib/metrics'

const ALWAYS_SHOWN = ['CPU', 'MEMORY', 'DISK']
const SHOWN_WHEN_PRESENT = ['LATENCY_MS', 'ERROR_RATE']

const SEVERITY_COLORS = { CRITICAL: '#ef4444', WARNING: '#f59e0b' }
const RECENT_INCIDENTS = { size: 100, sort: 'openedAt,desc' }

export function ServiceMetrics({ serviceId, range, rules }) {
  const metrics = useQuery({
    queryKey: queryKeys.serviceMetrics(serviceId, range),
    queryFn: () => fetchServiceMetrics(serviceId, range),
    refetchInterval: 15_000,
    placeholderData: keepPreviousData,
  })
  const incidentParams = { serviceId, ...RECENT_INCIDENTS }
  const incidents = useQuery({
    queryKey: queryKeys.incidents(incidentParams),
    queryFn: () => fetchIncidents(incidentParams),
    refetchInterval: 15_000,
  })

  if (metrics.isPending) {
    return (
      <div className="grid gap-4 lg:grid-cols-2">
        {ALWAYS_SHOWN.map((metric) => (
          <Skeleton key={metric} className="h-72" />
        ))}
      </div>
    )
  }
  if (metrics.isError) {
    return (
      <div className="card">
        <ErrorState error={metrics.error} onRetry={metrics.refetch} title="Could not load metrics" />
      </div>
    )
  }

  const points = metrics.data.points ?? []
  if (points.length === 0) {
    return (
      <div className="card">
        <EmptyState title="No samples in this time range" description="Try a longer range or check that the agent is running." />
      </div>
    )
  }

  const shownMetrics = [
    ...ALWAYS_SHOWN,
    ...SHOWN_WHEN_PRESENT.filter((metric) => hasAnyValue(points, METRICS[metric].field)),
  ]
  const rangeSeconds = TIME_RANGES.find((option) => option.value === range)?.seconds
  const windows = incidentWindows(incidents.data?.content ?? [])

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      {shownMetrics.map((metric) => (
        <section key={metric} className="card p-4" aria-label={`${METRICS[metric].label} chart`}>
          <h2 className="mb-2 text-sm font-semibold text-zinc-100">
            {METRICS[metric].label}
            <span className="ml-2 text-xs font-normal text-subtle">{METRICS[metric].unit}</span>
          </h2>
          <MetricChart
            data={toChartData(points, METRICS[metric].field, metrics.data.bucketSeconds)}
            unit={METRICS[metric].unit}
            thresholds={thresholdsForMetric(rules, metric)}
            highlights={windows.filter((window) => window.metric === metric)}
            rangeSeconds={rangeSeconds}
          />
        </section>
      ))}
      {windows.length > 0 && (
        <p className="text-xs text-subtle lg:col-span-2">
          Shaded areas are incidents on this service, drawn on the chart of the metric that triggered them
          (red = critical, amber = warning).
        </p>
      )}
    </div>
  )
}

// Each incident becomes a shaded window from openedAt to resolvedAt (or now, if it is still active).
function incidentWindows(incidents) {
  return incidents.map((incident) => ({
    id: incident.id,
    metric: incident.metric,
    from: new Date(incident.openedAt).getTime(),
    to: incident.resolvedAt ? new Date(incident.resolvedAt).getTime() : Date.now(),
    color: SEVERITY_COLORS[incident.severity] ?? SEVERITY_COLORS.WARNING,
  }))
}
