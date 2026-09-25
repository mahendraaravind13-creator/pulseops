import { CheckCheck, CircleAlert, CircleCheck, FileText, MessageSquare, RotateCcw, Siren, Sparkles } from 'lucide-react'
import { useNow } from '../../hooks/useNow'
import { formatRelativeTime } from '../../lib/format'

const EVENT_STYLES = {
  OPENED: { icon: Siren, color: 'text-red-400 bg-red-500/10', label: 'Opened' },
  ACKNOWLEDGED: { icon: CheckCheck, color: 'text-amber-300 bg-amber-500/10', label: 'Acknowledged' },
  RESOLVED: { icon: CircleCheck, color: 'text-emerald-400 bg-emerald-500/10', label: 'Resolved' },
  AUTO_RESOLVED: { icon: RotateCcw, color: 'text-emerald-400 bg-emerald-500/10', label: 'Auto-resolved' },
  NOTE: { icon: MessageSquare, color: 'text-zinc-300 bg-zinc-500/10', label: 'Note' },
  ANALYSIS_COMPLETED: { icon: Sparkles, color: 'text-purple-300 bg-purple-500/10', label: 'Analysis completed' },
  ANALYSIS_FAILED: { icon: CircleAlert, color: 'text-red-400 bg-red-500/10', label: 'Analysis failed' },
  POSTMORTEM_READY: { icon: FileText, color: 'text-sky-300 bg-sky-500/10', label: 'Post-mortem ready' },
}

const FALLBACK_STYLE = { icon: MessageSquare, color: 'text-zinc-300 bg-zinc-500/10', label: 'Event' }

export function IncidentTimeline({ events }) {
  const now = useNow(10_000)

  if (events.length === 0) {
    return <p className="text-sm text-muted">No events yet.</p>
  }

  const ordered = [...events].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))

  return (
    <ol className="relative space-y-4">
      {ordered.map((event, index) => {
        const style = EVENT_STYLES[event.type] ?? FALLBACK_STYLE
        const Icon = style.icon
        const isLast = index === ordered.length - 1
        return (
          <li key={event.id} className="relative flex gap-3">
            {!isLast && <span className="absolute left-[13px] top-7 h-[calc(100%-4px)] w-px bg-line" aria-hidden="true" />}
            <span className={`relative flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${style.color}`}>
              <Icon className="h-3.5 w-3.5" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1 pt-0.5">
              <p className="text-sm text-zinc-100">
                <span className="font-medium">{style.label}</span>
                {event.actor && <span className="text-muted"> by {event.actor}</span>}
              </p>
              {event.message && <p className="mt-0.5 whitespace-pre-line break-words text-sm text-muted">{event.message}</p>}
              <time dateTime={event.createdAt} title={new Date(event.createdAt).toLocaleString()} className="text-xs text-subtle">
                {formatRelativeTime(event.createdAt, now)}
              </time>
            </div>
          </li>
        )
      })}
    </ol>
  )
}
