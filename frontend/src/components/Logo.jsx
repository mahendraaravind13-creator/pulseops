import { Activity } from 'lucide-react'

export function Logo({ className = '' }) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-500">
        <Activity className="h-4 w-4 text-emerald-950" strokeWidth={2.6} aria-hidden="true" />
      </span>
      <span className="text-base font-semibold tracking-tight text-zinc-50">PulseOps</span>
    </span>
  )
}
