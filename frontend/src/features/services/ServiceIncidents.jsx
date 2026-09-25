import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchIncidents } from '../../api/incidents'
import { queryKeys } from '../../api/queryKeys'
import { DataTable } from '../../components/DataTable'
import { ErrorState } from '../../components/ErrorState'
import { Pagination } from '../../components/Pagination'
import { SeverityBadge } from '../../components/SeverityBadge'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime, formatDuration, secondsBetween } from '../../lib/format'

const PAGE_SIZE = 10

export function ServiceIncidents({ serviceId }) {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const params = { serviceId, page, size: PAGE_SIZE, sort: 'openedAt,desc' }

  const incidents = useQuery({
    queryKey: queryKeys.incidents(params),
    queryFn: () => fetchIncidents(params),
    refetchInterval: 15_000,
    placeholderData: keepPreviousData,
  })

  const columns = [
    { key: 'title', header: 'Incident', render: (incident) => <span className="text-zinc-100">{incident.title}</span> },
    { key: 'severity', header: 'Severity', render: (incident) => <SeverityBadge severity={incident.severity} /> },
    { key: 'status', header: 'Status', render: (incident) => <StatusBadge status={incident.status} /> },
    { key: 'openedAt', header: 'Opened', render: (incident) => formatDateTime(incident.openedAt) },
    {
      key: 'duration',
      header: 'Duration',
      align: 'right',
      render: (incident) => formatDuration(secondsBetween(incident.openedAt, incident.resolvedAt)),
    },
  ]

  return (
    <section className="card" aria-labelledby="service-incidents-title">
      <h2 id="service-incidents-title" className="border-b border-line px-4 py-3 text-sm font-semibold text-zinc-100">
        Incidents
      </h2>
      {incidents.isError ? (
        <ErrorState error={incidents.error} onRetry={incidents.refetch} title="Could not load incidents" />
      ) : (
        <>
          <DataTable
            caption="Incidents for this service"
            columns={columns}
            rows={incidents.data?.content ?? []}
            isLoading={incidents.isPending}
            skeletonRows={3}
            onRowClick={(incident) => navigate(`/incidents/${incident.id}`)}
            emptyMessage="This service has no incidents."
          />
          <Pagination page={incidents.data?.page} onPageChange={setPage} />
        </>
      )}
    </section>
  )
}
