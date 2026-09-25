import { Link } from 'react-router-dom'
import { METRICS, OPERATORS } from '../../lib/constants'
import { formatDateTime, formatMetricValue, formatNumber } from '../../lib/format'
import { formatThreshold } from '../../lib/rules'

function describeCondition(condition) {
  if (!condition) return '—'
  const metric = METRICS[condition.metric]?.label ?? condition.metric
  const symbol = OPERATORS[condition.operator]?.symbol ?? condition.operator
  const timing = condition.durationSeconds > 0 ? ` for ${condition.durationSeconds}s` : ''
  return `${metric} ${symbol} ${formatThreshold(condition.metric, condition.threshold)}${timing}`
}

export function IncidentFacts({ incident }) {
  const facts = [
    {
      label: 'Rule',
      value: (
        <Link to="/rules" className="text-emerald-400 hover:text-emerald-300">
          {incident.ruleName}
        </Link>
      ),
    },
    { label: 'Condition', value: describeCondition(incident.condition) },
    { label: 'Peak value', value: formatMetricValue(incident.metric, incident.peakValue) },
    { label: 'Breaches', value: formatNumber(incident.breachCount) },
    { label: 'Last breach', value: formatDateTime(incident.lastBreachAt) },
    { label: 'Acknowledged', value: incident.acknowledgedAt ? `${formatDateTime(incident.acknowledgedAt)}${incident.acknowledgedBy ? ` by ${incident.acknowledgedBy}` : ''}` : '—' },
    {
      label: 'Resolved',
      value: incident.resolvedAt
        ? `${formatDateTime(incident.resolvedAt)}${incident.autoResolved ? ' (automatically)' : incident.resolvedBy ? ` by ${incident.resolvedBy}` : ''}`
        : '—',
    },
  ]

  return (
    <section className="card p-4" aria-labelledby="facts-title">
      <h2 id="facts-title" className="mb-3 text-sm font-semibold text-zinc-100">
        Details
      </h2>
      <dl className="space-y-2.5 text-sm">
        {facts.map((fact) => (
          <div key={fact.label} className="flex justify-between gap-4">
            <dt className="shrink-0 text-subtle">{fact.label}</dt>
            <dd className="min-w-0 text-right text-zinc-200">{fact.value}</dd>
          </div>
        ))}
      </dl>
      {incident.resolutionNote && (
        <div className="mt-4 border-t border-line pt-3">
          <p className="text-xs font-medium uppercase tracking-wide text-subtle">Resolution note</p>
          <p className="mt-1 whitespace-pre-line text-sm text-zinc-200">{incident.resolutionNote}</p>
        </div>
      )}
    </section>
  )
}
