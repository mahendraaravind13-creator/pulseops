import { Link, useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchIncidents } from '../../api/incidents'
import { queryKeys } from '../../api/queryKeys'
import { DataTable } from '../../components/DataTable'
import { ErrorState } from '../../components/ErrorState'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { SeverityBadge } from '../../components/SeverityBadge'
import { Spinner } from '../../components/Spinner'
import { StatusBadge } from '../../components/StatusBadge'
import { useNow } from '../../hooks/useNow'
import { formatDuration, formatMetricValue, formatRelativeTime, secondsBetween } from '../../lib/format'
import { nextSort, parseSortParam, toSortParam } from '../../lib/sort'
import { IncidentFilters } from './IncidentFilters'
import { DEFAULT_SORT, toApiParams, useIncidentFilters } from './useIncidentFilters'

export function IncidentsPage() {
  const navigate = useNavigate()
  const now = useNow(5000)
  const { filters, updateFilters, clearFilters, hasActiveFilters } = useIncidentFilters()
  const params = toApiParams(filters)
  const sort = parseSortParam(filters.sort, DEFAULT_SORT)

  const incidents = useQuery({
    queryKey: queryKeys.incidents(params),
    queryFn: () => fetchIncidents(params),
    refetchInterval: 15_000,
    placeholderData: keepPreviousData,
  })

  const columns = [
    {
      key: 'title',
      header: 'Incident',
      render: (incident) => (
        <div className="min-w-0 max-w-md">
          <Link to={`/incidents/${incident.id}`} className="font-medium text-zinc-100 hover:text-emerald-300">
            {incident.title}
          </Link>
          <p className="text-xs text-subtle">
            {incident.serviceName} · {incident.ruleName}
          </p>
        </div>
      ),
    },
    { key: 'severity', header: 'Severity', sortable: true, render: (i) => <SeverityBadge severity={i.severity} /> },
    { key: 'status', header: 'Status', sortable: true, render: (i) => <StatusBadge status={i.status} /> },
    {
      key: 'peakValue',
      header: 'Peak',
      align: 'right',
      render: (incident) => formatMetricValue(incident.metric, incident.peakValue),
    },
    {
      key: 'openedAt',
      header: 'Opened',
      sortable: true,
      render: (incident) => (
        <span className="text-muted" title={new Date(incident.openedAt).toLocaleString()}>
          {formatRelativeTime(incident.openedAt, now)}
        </span>
      ),
    },
    {
      key: 'duration',
      header: 'Duration',
      align: 'right',
      render: (incident) => formatDuration(secondsBetween(incident.openedAt, incident.resolvedAt)),
    },
  ]

  return (
    <>
      <PageHeader
        title="Incidents"
        description="Your team's work queue. Filters are saved in the URL, so you can share a view."
        actions={incidents.isFetching && !incidents.isPending ? <Spinner size="sm" className="text-subtle" /> : null}
      />
      <div className="card">
        <IncidentFilters
          filters={filters}
          onChange={updateFilters}
          onClear={clearFilters}
          hasActiveFilters={hasActiveFilters}
        />
        {incidents.isError ? (
          <ErrorState error={incidents.error} onRetry={incidents.refetch} title="Could not load incidents" />
        ) : (
          <>
            <DataTable
              caption="Incidents"
              columns={columns}
              rows={incidents.data?.content ?? []}
              isLoading={incidents.isPending}
              sort={sort}
              onSort={(key) => updateFilters({ sort: toSortParam(nextSort(sort, key)) })}
              onRowClick={(incident) => navigate(`/incidents/${incident.id}`)}
              emptyMessage={
                hasActiveFilters ? (
                  <span>
                    No incidents match these filters.{' '}
                    <button type="button" onClick={clearFilters} className="font-medium text-emerald-400 hover:text-emerald-300">
                      Clear filters
                    </button>
                  </span>
                ) : (
                  'No incidents yet. They open automatically when an alert rule fires.'
                )
              }
            />
            <Pagination page={incidents.data?.page} onPageChange={(page) => updateFilters({ page })} />
          </>
        )}
      </div>
    </>
  )
}
