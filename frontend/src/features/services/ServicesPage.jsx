import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { fetchServices } from '../../api/services'
import { queryKeys } from '../../api/queryKeys'
import { DataTable } from '../../components/DataTable'
import { ErrorState } from '../../components/ErrorState'
import { PageHeader } from '../../components/PageHeader'
import { StatusBadge, StatusDot } from '../../components/StatusBadge'
import { useNow } from '../../hooks/useNow'
import { formatPercent, formatRelativeTime } from '../../lib/format'
import { compareValues, nextSort } from '../../lib/sort'

const STATUS_RANK = { CRITICAL: 0, WARNING: 1, STALE: 2, HEALTHY: 3 }

const SORT_VALUE = {
  name: (service) => service.name.toLowerCase(),
  status: (service) => STATUS_RANK[service.status] ?? 9,
  cpu: (service) => service.latest?.cpu ?? null,
  memory: (service) => service.latest?.memory ?? null,
  lastSeenAt: (service) => (service.lastSeenAt ? -new Date(service.lastSeenAt).getTime() : null),
  openIncidents: (service) => service.openIncidents,
}

export function ServicesPage() {
  const navigate = useNavigate()
  const now = useNow(5000)
  const [search, setSearch] = useState('')
  const [sort, setSort] = useState({ key: 'status', direction: 'asc' })

  const services = useQuery({ queryKey: queryKeys.services, queryFn: fetchServices, refetchInterval: 15_000 })

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase()
    const filtered = (services.data ?? []).filter(
      (service) =>
        !term || service.name.toLowerCase().includes(term) || (service.hostname ?? '').toLowerCase().includes(term),
    )
    const getValue = SORT_VALUE[sort.key]
    const direction = sort.direction === 'asc' ? 1 : -1
    return [...filtered].sort((a, b) => direction * compareValues(getValue(a), getValue(b)))
  }, [services.data, search, sort])

  const columns = [
    {
      key: 'name',
      header: 'Service',
      sortable: true,
      render: (service) => (
        <div className="flex items-center gap-2.5">
          <StatusDot status={service.status} />
          <div className="min-w-0">
            <Link to={`/services/${service.id}`} className="font-medium text-zinc-100 hover:text-emerald-300">
              {service.name}
            </Link>
            <p className="text-xs text-subtle">{service.hostname ?? '—'}</p>
          </div>
        </div>
      ),
    },
    { key: 'status', header: 'Status', sortable: true, render: (service) => <StatusBadge status={service.status} /> },
    { key: 'cpu', header: 'CPU', sortable: true, align: 'right', render: (s) => formatPercent(s.latest?.cpu) },
    { key: 'memory', header: 'Memory', sortable: true, align: 'right', render: (s) => formatPercent(s.latest?.memory) },
    {
      key: 'lastSeenAt',
      header: 'Last seen',
      sortable: true,
      render: (service) => (
        <span className={service.status === 'STALE' ? 'text-amber-300' : 'text-muted'}>
          {formatRelativeTime(service.lastSeenAt, now)}
        </span>
      ),
    },
    {
      key: 'openIncidents',
      header: 'Open incidents',
      sortable: true,
      align: 'right',
      render: (service) => (
        <span className={service.openIncidents > 0 ? 'font-semibold text-red-300' : 'text-subtle'}>
          {service.openIncidents}
        </span>
      ),
    },
  ]

  return (
    <>
      <PageHeader title="Services" description="Every service that has reported through the agent." />
      <div className="card">
        <div className="border-b border-line p-3">
          <label className="relative block max-w-sm">
            <span className="sr-only">Search services</span>
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-subtle" aria-hidden="true" />
            <input
              type="search"
              className="input pl-9"
              placeholder="Search by name or host"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
        </div>
        {services.isError ? (
          <ErrorState error={services.error} onRetry={services.refetch} title="Could not load services" />
        ) : (
          <DataTable
            caption="Services"
            columns={columns}
            rows={rows}
            isLoading={services.isPending}
            sort={sort}
            onSort={(key) => setSort((current) => nextSort(current, key, 'asc'))}
            onRowClick={(service) => navigate(`/services/${service.id}`)}
            emptyMessage={
              search ? 'No services match your search.' : 'No services yet. Install the agent to start reporting.'
            }
          />
        )}
      </div>
    </>
  )
}
