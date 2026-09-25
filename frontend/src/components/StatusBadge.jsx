import { toLabel } from '../lib/format'

const GREEN = 'bg-emerald-500/10 text-emerald-300 ring-emerald-500/30'
const AMBER = 'bg-amber-500/10 text-amber-300 ring-amber-500/30'
const RED = 'bg-red-500/10 text-red-300 ring-red-500/30'
const GRAY = 'bg-zinc-500/10 text-zinc-300 ring-zinc-500/30'
const PURPLE = 'bg-purple-500/10 text-purple-300 ring-purple-500/30'

const STYLES = {
  OPEN: RED,
  ACKNOWLEDGED: AMBER,
  RESOLVED: GREEN,
  HEALTHY: GREEN,
  WARNING: AMBER,
  CRITICAL: RED,
  STALE: GRAY,
  PENDING: PURPLE,
  DELIVERED: GREEN,
  COMPLETED: GREEN,
  FAILED: RED,
  DISABLED: GRAY,
}

const DOT_COLORS = {
  HEALTHY: 'bg-emerald-400',
  WARNING: 'bg-amber-400',
  CRITICAL: 'bg-red-500',
  STALE: 'bg-zinc-500',
}

export function StatusBadge({ status, className = '' }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${STYLES[status] ?? GRAY} ${className}`}
    >
      {toLabel(status)}
    </span>
  )
}

export function StatusDot({ status, className = '' }) {
  const pulse = status === 'CRITICAL' ? 'animate-pulse' : ''
  return (
    <span
      className={`inline-block h-2.5 w-2.5 shrink-0 rounded-full ${DOT_COLORS[status] ?? 'bg-zinc-500'} ${pulse} ${className}`}
      aria-hidden="true"
    />
  )
}
