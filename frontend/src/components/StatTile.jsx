import { Skeleton } from './Skeleton'

const TONES = {
  default: 'text-zinc-50',
  danger: 'text-red-400',
  warning: 'text-amber-300',
  success: 'text-emerald-400',
}

export function StatTile({ label, value, hint, icon: Icon, tone = 'default', isLoading = false }) {
  return (
    <div className="card p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-subtle">{label}</p>
        {Icon && <Icon className="h-4 w-4 text-subtle" aria-hidden="true" />}
      </div>
      {isLoading ? (
        <Skeleton className="mt-3 h-8 w-20" />
      ) : (
        <p className={`mt-2 text-2xl font-semibold tabular-nums ${TONES[tone]}`}>{value}</p>
      )}
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  )
}
