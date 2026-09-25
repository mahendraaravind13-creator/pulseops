import { formatPercent } from '../lib/format'

function barColor(value) {
  if (value >= 85) return 'bg-red-500'
  if (value >= 70) return 'bg-amber-400'
  return 'bg-emerald-500'
}

export function UsageBar({ label, value }) {
  const hasValue = value !== null && value !== undefined
  const width = hasValue ? Math.min(Math.max(value, 0), 100) : 0
  return (
    <div>
      <div className="mb-1 flex justify-between text-xs">
        <span className="text-subtle">{label}</span>
        <span className="tabular-nums text-zinc-300">{formatPercent(value)}</span>
      </div>
      <div
        className="h-1.5 overflow-hidden rounded-full bg-zinc-800"
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={hasValue ? Number(value.toFixed(1)) : undefined}
      >
        <div className={`h-full rounded-full ${barColor(value)}`} style={{ width: `${width}%` }} />
      </div>
    </div>
  )
}
