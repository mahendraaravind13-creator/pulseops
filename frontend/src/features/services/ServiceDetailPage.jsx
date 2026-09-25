import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { fetchRules } from '../../api/rules'
import { fetchService } from '../../api/services'
import { queryKeys } from '../../api/queryKeys'
import { ErrorState } from '../../components/ErrorState'
import { PageSpinner } from '../../components/Spinner'
import { StatusBadge, StatusDot } from '../../components/StatusBadge'
import { TimeRangePicker } from '../../components/TimeRangePicker'
import { RelativeTime } from '../../components/RelativeTime'
import { formatMetricValue } from '../../lib/format'
import { rulesForService } from '../../lib/rules'
import { ServiceIncidents } from './ServiceIncidents'
import { ServiceMetrics } from './ServiceMetrics'

export function ServiceDetailPage() {
  const serviceId = Number(useParams().id)
  const [range, setRange] = useState('1h')

  const service = useQuery({
    queryKey: queryKeys.service(serviceId),
    queryFn: () => fetchService(serviceId),
    refetchInterval: 15_000,
  })
  const rules = useQuery({ queryKey: queryKeys.rules, queryFn: fetchRules })

  if (service.isPending) return <PageSpinner />
  if (service.isError) {
    return (
      <div className="card">
        <ErrorState error={service.error} onRetry={service.refetch} title="Could not load service" />
      </div>
    )
  }

  const data = service.data
  const applicableRules = rulesForService(rules.data ?? [], serviceId)

  return (
    <>
      <Link to="/services" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-zinc-100">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Services
      </Link>

      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <StatusDot status={data.status} />
            <h1 className="page-title truncate">{data.name}</h1>
            <StatusBadge status={data.status} />
          </div>
          <p className="mt-1 text-sm text-muted">
            {data.hostname ?? 'unknown host'} · last seen <RelativeTime iso={data.lastSeenAt} />
          </p>
        </div>
        <TimeRangePicker value={range} onChange={setRange} />
      </div>

      {data.latest && (
        <dl className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-5">
          <LatestValue label="CPU" value={formatMetricValue('CPU', data.latest.cpu)} />
          <LatestValue label="Memory" value={formatMetricValue('MEMORY', data.latest.memory)} />
          <LatestValue label="Disk" value={formatMetricValue('DISK', data.latest.disk)} />
          <LatestValue label="Latency" value={formatMetricValue('LATENCY_MS', data.latest.latencyMs)} />
          <LatestValue label="Error rate" value={formatMetricValue('ERROR_RATE', data.latest.errorRate)} />
        </dl>
      )}

      <ServiceMetrics serviceId={serviceId} range={range} rules={applicableRules} />

      <div className="mt-6">
        <ServiceIncidents serviceId={serviceId} />
      </div>
    </>
  )
}

function LatestValue({ label, value }) {
  return (
    <div className="card px-4 py-3">
      <dt className="text-xs text-subtle">{label}</dt>
      <dd className="mt-1 text-lg font-semibold tabular-nums text-zinc-100">{value}</dd>
    </div>
  )
}
