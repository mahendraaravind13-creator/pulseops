import { useQuery } from '@tanstack/react-query'
import { Clock, Server, Siren, Timer, TriangleAlert } from 'lucide-react'
import { fetchOverview } from '../../api/overview'
import { queryKeys } from '../../api/queryKeys'
import { ErrorState } from '../../components/ErrorState'
import { PageHeader } from '../../components/PageHeader'
import { StatTile } from '../../components/StatTile'
import { formatDuration } from '../../lib/format'
import { ActiveIncidentsList } from './ActiveIncidentsList'
import { IncidentsPerDayChart } from './IncidentsPerDayChart'
import { ServiceHealthGrid } from './ServiceHealthGrid'

const REFRESH_MS = 10_000

export function OverviewPage() {
  const overview = useQuery({
    queryKey: queryKeys.overview,
    queryFn: fetchOverview,
    refetchInterval: REFRESH_MS,
  })

  return (
    <>
      <PageHeader title="Overview" description="Is anything on fire right now?" />
      {overview.isError ? (
        <div className="card mb-6">
          <ErrorState error={overview.error} onRetry={overview.refetch} title="Could not load overview" />
        </div>
      ) : (
        <StatTiles data={overview.data} isLoading={overview.isPending} />
      )}

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <div className="xl:col-span-2">
          <ServiceHealthGrid refetchInterval={REFRESH_MS} />
        </div>
        <IncidentsPerDayChart data={overview.data?.incidentsPerDay} isLoading={overview.isPending} isError={overview.isError} />
      </div>

      <div className="mt-6">
        <ActiveIncidentsList refetchInterval={REFRESH_MS} />
      </div>
    </>
  )
}

function StatTiles({ data, isLoading }) {
  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-5">
      <StatTile
        label="Open incidents"
        icon={Siren}
        isLoading={isLoading}
        value={data?.openIncidents ?? 0}
        tone={data?.openIncidents > 0 ? 'danger' : 'success'}
        hint={data ? `${data.acknowledgedIncidents} acknowledged` : undefined}
      />
      <StatTile
        label="Critical"
        icon={TriangleAlert}
        isLoading={isLoading}
        value={data?.criticalOpen ?? 0}
        tone={data?.criticalOpen > 0 ? 'danger' : 'default'}
        hint="Open or acknowledged"
      />
      <StatTile
        label="Services reporting"
        icon={Server}
        isLoading={isLoading}
        value={data ? `${data.servicesReporting}/${data.servicesTotal}` : '—'}
        tone={data && data.servicesReporting < data.servicesTotal ? 'warning' : 'default'}
        hint="Sample in the last 60s"
      />
      <StatTile
        label="MTTA"
        icon={Clock}
        isLoading={isLoading}
        value={formatDuration(data?.mttaSeconds)}
        hint="Mean time to acknowledge, 7d"
      />
      <StatTile
        label="MTTR"
        icon={Timer}
        isLoading={isLoading}
        value={formatDuration(data?.mttrSeconds)}
        hint="Mean time to resolve, 7d"
      />
    </div>
  )
}
