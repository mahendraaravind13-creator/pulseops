import { useQuery } from '@tanstack/react-query'
import { fetchSettings } from '../../api/settings'
import { queryKeys } from '../../api/queryKeys'
import { AgentSnippet } from '../../components/AgentSnippet'
import { ErrorState } from '../../components/ErrorState'
import { PageHeader } from '../../components/PageHeader'
import { PageSpinner } from '../../components/Spinner'
import { useAuth } from '../../hooks/useAuth'
import { formatDateTime, formatNumber } from '../../lib/format'
import { ApiKeyCard } from './ApiKeyCard'
import { SettingsCard } from './SettingsCard'
import { WebhookCard } from './WebhookCard'
import { WebhookDeliveries } from './WebhookDeliveries'

export function SettingsPage() {
  const { isOwner } = useAuth()
  const settings = useQuery({ queryKey: queryKeys.settings, queryFn: fetchSettings })

  if (settings.isPending) return <PageSpinner />
  if (settings.isError) {
    return (
      <div className="card">
        <ErrorState error={settings.error} onRetry={settings.refetch} title="Could not load settings" />
      </div>
    )
  }

  const { tenant, apiKeyPrefix, rateLimitPerMinute, retentionDays, webhookUrl } = settings.data

  return (
    <>
      <PageHeader title="Settings" description={isOwner ? 'Manage your organisation.' : 'Only the owner can change settings.'} />
      <div className="grid gap-6 lg:grid-cols-2">
        <SettingsCard title="Organisation">
          <dl className="space-y-2.5 text-sm">
            <Row label="Name" value={tenant.name} />
            <Row label="Slug" value={<span className="font-mono">{tenant.slug}</span>} />
            <Row label="Created" value={formatDateTime(tenant.createdAt)} />
            <Row label="Ingest rate limit" value={`${formatNumber(rateLimitPerMinute)} samples / minute`} />
            <Row label="Metric retention" value={`${retentionDays} days`} />
          </dl>
        </SettingsCard>

        <ApiKeyCard apiKeyPrefix={apiKeyPrefix} isOwner={isOwner} />

        <SettingsCard title="Install the agent" className="lg:col-span-2">
          <AgentSnippet />
        </SettingsCard>

        <WebhookCard webhookUrl={webhookUrl} isOwner={isOwner} />
        <WebhookDeliveries />
      </div>
    </>
  )
}

function Row({ label, value }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-subtle">{label}</dt>
      <dd className="text-right text-zinc-200">{value}</dd>
    </div>
  )
}
