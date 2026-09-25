import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Server } from 'lucide-react'
import { fetchServices } from '../../api/services'
import { queryKeys } from '../../api/queryKeys'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { Skeleton } from '../../components/Skeleton'
import { StatusDot } from '../../components/StatusBadge'
import { UsageBar } from '../../components/UsageBar'
import { useNow } from '../../hooks/useNow'
import { formatRelativeTime, toLabel } from '../../lib/format'

export function ServiceHealthGrid({ refetchInterval }) {
  const services = useQuery({ queryKey: queryKeys.services, queryFn: fetchServices, refetchInterval })

  return (
    <section aria-labelledby="service-health-title">
      <div className="mb-3 flex items-baseline justify-between">
        <h2 id="service-health-title" className="text-sm font-semibold text-zinc-100">
          Service health
        </h2>
        <Link to="/services" className="text-xs font-medium text-emerald-400 hover:text-emerald-300">
          View all
        </Link>
      </div>
      <ServiceHealthContent query={services} />
    </section>
  )
}

function ServiceHealthContent({ query }) {
  const now = useNow(1000)

  if (query.isPending) {
    return (
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 3 }, (_, index) => (
          <Skeleton key={index} className="h-36" />
        ))}
      </div>
    )
  }
  if (query.isError) {
    return (
      <div className="card">
        <ErrorState error={query.error} onRetry={query.refetch} title="Could not load services" />
      </div>
    )
  }
  if (query.data.length === 0) {
    return (
      <div className="card">
        <EmptyState
          icon={Server}
          title="No services reporting yet"
          description="Install the agent with your API key and services appear here automatically."
          action={
            <Link to="/settings" className="text-sm font-medium text-emerald-400 hover:text-emerald-300">
              Get the install command
            </Link>
          }
        />
      </div>
    )
  }

  const worstFirst = [...query.data].sort(
    (a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status] || a.name.localeCompare(b.name),
  )
  const visible = worstFirst.slice(0, MAX_CARDS)
  const hidden = worstFirst.length - visible.length

  return (
    <div className="space-y-3">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {visible.map((service) => (
          <ServiceHealthCard key={service.id} service={service} now={now} />
        ))}
      </div>
      {hidden > 0 && (
        <Link to="/services" className="block text-center text-xs font-medium text-emerald-400 hover:text-emerald-300">
          {hidden} more {hidden === 1 ? 'service' : 'services'} not shown · view all {worstFirst.length}
        </Link>
      )}
    </div>
  )
}

// The overview answers "is anything wrong?", so problems come first and the grid stays short.
const MAX_CARDS = 9
const STATUS_ORDER = { CRITICAL: 0, WARNING: 1, STALE: 2, HEALTHY: 3 }

function ServiceHealthCard({ service, now }) {
  return (
    <Link
      to={`/services/${service.id}`}
      className="card block p-4 transition-colors hover:border-line-strong hover:bg-panel-raised"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <StatusDot status={service.status} />
            <p className="truncate text-sm font-semibold text-zinc-100">{service.name}</p>
          </div>
          <p className="mt-0.5 truncate text-xs text-subtle">{service.hostname ?? 'unknown host'}</p>
        </div>
        <span className="shrink-0 text-xs text-muted">{toLabel(service.status)}</span>
      </div>
      <div className="mt-4 space-y-2.5">
        <UsageBar label="CPU" value={service.latest?.cpu} />
        <UsageBar label="Memory" value={service.latest?.memory} />
      </div>
      <div className="mt-3 flex items-center justify-between text-xs text-subtle">
        <span>Last seen {formatRelativeTime(service.lastSeenAt, now)}</span>
        {service.openIncidents > 0 && (
          <span className="text-red-300">
            {service.openIncidents} open {service.openIncidents === 1 ? 'incident' : 'incidents'}
          </span>
        )}
      </div>
    </Link>
  )
}
