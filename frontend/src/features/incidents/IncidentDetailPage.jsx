import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, CheckCheck, CircleCheck } from 'lucide-react'
import { fetchIncident } from '../../api/incidents'
import { queryKeys } from '../../api/queryKeys'
import { Button } from '../../components/Button'
import { ErrorState } from '../../components/ErrorState'
import { SeverityBadge } from '../../components/SeverityBadge'
import { PageSpinner } from '../../components/Spinner'
import { StatusBadge } from '../../components/StatusBadge'
import { useNow } from '../../hooks/useNow'
import { formatDateTime, formatDuration, secondsBetween } from '../../lib/format'
import { AddNoteForm } from './AddNoteForm'
import { AnalysisPanel } from './AnalysisPanel'
import { IncidentFacts } from './IncidentFacts'
import { IncidentMetricChart } from './IncidentMetricChart'
import { IncidentTimeline } from './IncidentTimeline'
import { PostmortemSection } from './PostmortemSection'
import { ResolveModal } from './ResolveModal'
import { useIncidentActions } from './useIncidentActions'

function refetchIntervalFor(incident) {
  const analysis = incident?.analysis
  const isGenerating = analysis?.status === 'PENDING' || analysis?.postmortemStatus === 'PENDING'
  return isGenerating ? 5_000 : 15_000
}

export function IncidentDetailPage() {
  const incidentId = Number(useParams().id)
  const [isResolveOpen, setResolveOpen] = useState(false)
  const actions = useIncidentActions(incidentId)

  const incidentQuery = useQuery({
    queryKey: queryKeys.incident(incidentId),
    queryFn: () => fetchIncident(incidentId),
    refetchInterval: (query) => refetchIntervalFor(query.state.data),
  })

  if (incidentQuery.isPending) return <PageSpinner />
  if (incidentQuery.isError) {
    return (
      <div className="card">
        <ErrorState error={incidentQuery.error} onRetry={incidentQuery.refetch} title="Could not load incident" />
      </div>
    )
  }

  const incident = incidentQuery.data

  function handleResolve(note) {
    actions.resolve.mutate(
      { version: incident.version, note },
      { onSettled: () => setResolveOpen(false) },
    )
  }

  return (
    <>
      <Link to="/incidents" className="mb-4 inline-flex items-center gap-1 text-sm text-muted hover:text-zinc-100">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Incidents
      </Link>

      <IncidentHeader
        incident={incident}
        onAcknowledge={() => actions.acknowledge.mutate(incident.version)}
        isAcknowledging={actions.acknowledge.isPending}
        onResolve={() => setResolveOpen(true)}
        isResolving={actions.resolve.isPending}
      />

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <IncidentMetricChart incident={incident} />
          <AnalysisPanel
            analysis={incident.analysis}
            onRetry={() => actions.retryAnalysis.mutate()}
            isRetrying={actions.retryAnalysis.isPending}
          />
          {incident.status === 'RESOLVED' && (
            <PostmortemSection
              analysis={incident.analysis}
              onRetry={() => actions.retryPostmortem.mutate()}
              isRetrying={actions.retryPostmortem.isPending}
            />
          )}
        </div>
        <div className="space-y-6">
          <IncidentFacts incident={incident} />
          <section className="card p-4" aria-labelledby="timeline-title">
            <h2 id="timeline-title" className="mb-4 text-sm font-semibold text-zinc-100">
              Timeline
            </h2>
            <IncidentTimeline events={incident.events ?? []} />
            <div className="mt-4 border-t border-line pt-4">
              <AddNoteForm onSubmit={(message, options) => actions.addNote.mutate(message, options)} isPending={actions.addNote.isPending} />
            </div>
          </section>
        </div>
      </div>

      <ResolveModal
        isOpen={isResolveOpen}
        onClose={() => setResolveOpen(false)}
        onConfirm={handleResolve}
        isPending={actions.resolve.isPending}
      />
    </>
  )
}

function IncidentHeader({ incident, onAcknowledge, isAcknowledging, onResolve, isResolving }) {
  const now = useNow(1000)
  const canAcknowledge = incident.status === 'OPEN'
  const canResolve = incident.status === 'OPEN' || incident.status === 'ACKNOWLEDGED'
  const durationSeconds = secondsBetween(incident.openedAt, incident.resolvedAt ?? new Date(now).toISOString())

  return (
    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={incident.status} />
          <SeverityBadge severity={incident.severity} />
          <span className="text-xs text-subtle">#{incident.id}</span>
        </div>
        <h1 className="page-title mt-2 break-words">{incident.title}</h1>
        <p className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-sm text-muted">
          <Link to={`/services/${incident.serviceId}`} className="font-medium text-emerald-400 hover:text-emerald-300">
            {incident.serviceName}
          </Link>
          <span>Opened {formatDateTime(incident.openedAt)}</span>
          <span>
            {incident.resolvedAt ? 'Lasted' : 'Open for'} {formatDuration(durationSeconds)}
          </span>
        </p>
      </div>
      {canResolve && (
        <div className="flex shrink-0 flex-wrap gap-2">
          {canAcknowledge && (
            <Button icon={CheckCheck} onClick={onAcknowledge} isLoading={isAcknowledging} disabled={isResolving}>
              Acknowledge
            </Button>
          )}
          <Button variant="primary" icon={CircleCheck} onClick={onResolve} disabled={isAcknowledging || isResolving}>
            Resolve
          </Button>
        </div>
      )}
    </div>
  )
}
