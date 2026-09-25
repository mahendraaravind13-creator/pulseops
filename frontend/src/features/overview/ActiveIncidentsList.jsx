import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CircleCheck } from 'lucide-react'
import { fetchIncidents } from '../../api/incidents'
import { queryKeys } from '../../api/queryKeys'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { SeverityBadge } from '../../components/SeverityBadge'
import { Skeleton } from '../../components/Skeleton'
import { StatusBadge } from '../../components/StatusBadge'
import { useNow } from '../../hooks/useNow'
import { formatRelativeTime } from '../../lib/format'

const ACTIVE_PARAMS = { status: 'OPEN,ACKNOWLEDGED', sort: 'openedAt,desc', size: 10, page: 0 }

export function ActiveIncidentsList({ refetchInterval }) {
  const incidents = useQuery({
    queryKey: queryKeys.incidents(ACTIVE_PARAMS),
    queryFn: () => fetchIncidents(ACTIVE_PARAMS),
    refetchInterval,
  })

  return (
    <section className="card" aria-labelledby="active-incidents-title">
      <div className="flex items-baseline justify-between border-b border-line px-4 py-3">
        <h2 id="active-incidents-title" className="text-sm font-semibold text-zinc-100">
          Active incidents
        </h2>
        <Link
          to="/incidents?status=OPEN,ACKNOWLEDGED"
          className="text-xs font-medium text-emerald-400 hover:text-emerald-300"
        >
          View all
        </Link>
      </div>
      <ActiveIncidentsContent query={incidents} />
    </section>
  )
}

function ActiveIncidentsContent({ query }) {
  const now = useNow(5000)

  if (query.isPending) {
    return (
      <div className="space-y-3 p-4">
        {Array.from({ length: 3 }, (_, index) => (
          <Skeleton key={index} className="h-10" />
        ))}
      </div>
    )
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={query.refetch} title="Could not load incidents" />
  }
  const items = query.data.content
  if (items.length === 0) {
    return <EmptyState icon={CircleCheck} title="All clear" description="No open or acknowledged incidents." />
  }

  return (
    <ul className="divide-y divide-line">
      {items.map((incident) => (
        <li key={incident.id}>
          <Link
            to={`/incidents/${incident.id}`}
            className="flex flex-col gap-2 px-4 py-3 hover:bg-zinc-900/80 sm:flex-row sm:items-center sm:gap-4"
          >
            <div className="flex shrink-0 items-center gap-2">
              <SeverityBadge severity={incident.severity} />
              <StatusBadge status={incident.status} />
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-zinc-100">{incident.title}</p>
              <p className="text-xs text-subtle">{incident.serviceName}</p>
            </div>
            <span className="shrink-0 text-xs text-muted">Opened {formatRelativeTime(incident.openedAt, now)}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
