import { useQuery } from '@tanstack/react-query'
import { fetchWebhookDeliveries } from '../../api/settings'
import { queryKeys } from '../../api/queryKeys'
import { DataTable } from '../../components/DataTable'
import { ErrorState } from '../../components/ErrorState'
import { StatusBadge } from '../../components/StatusBadge'
import { formatDateTime, toLabel } from '../../lib/format'

const COLUMNS = [
  {
    key: 'event',
    header: 'Event',
    render: (delivery) => (
      <div className="min-w-0 max-w-[220px]">
        <p className="text-zinc-200">{toLabel(delivery.event)}</p>
        <p className="truncate text-xs text-subtle" title={delivery.title}>
          {delivery.title}
        </p>
      </div>
    ),
  },
  { key: 'status', header: 'Status', render: (delivery) => <StatusBadge status={delivery.status} /> },
  { key: 'attempts', header: 'Attempts', align: 'right' },
  { key: 'responseCode', header: 'Code', align: 'right', render: (delivery) => delivery.responseCode ?? '—' },
  {
    key: 'lastError',
    header: 'Error',
    render: (delivery) =>
      delivery.lastError ? (
        <span className="line-clamp-2 max-w-[200px] text-xs text-red-300" title={delivery.lastError}>
          {delivery.lastError}
        </span>
      ) : (
        <span className="text-subtle">—</span>
      ),
  },
  { key: 'createdAt', header: 'Created', render: (delivery) => <span className="text-muted">{formatDateTime(delivery.createdAt)}</span> },
]

export function WebhookDeliveries() {
  const deliveries = useQuery({
    queryKey: queryKeys.webhookDeliveries,
    queryFn: () => fetchWebhookDeliveries(20),
    refetchInterval: 15_000,
  })

  return (
    <section className="card lg:col-span-2" aria-labelledby="deliveries-title">
      <h2 id="deliveries-title" className="border-b border-line px-5 py-3 text-sm font-semibold text-zinc-100">
        Recent webhook deliveries
      </h2>
      {deliveries.isError ? (
        <ErrorState error={deliveries.error} onRetry={deliveries.refetch} title="Could not load deliveries" />
      ) : (
        <DataTable
          caption="Recent webhook deliveries"
          columns={COLUMNS}
          rows={deliveries.data ?? []}
          isLoading={deliveries.isPending}
          skeletonRows={3}
          emptyMessage="No deliveries yet. They appear here once a webhook URL is set and an incident opens or resolves."
        />
      )}
    </section>
  )
}
