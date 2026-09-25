import { Siren, TriangleAlert } from 'lucide-react'

const STYLES = {
  CRITICAL: { className: 'bg-red-500/15 text-red-300 ring-red-500/40', icon: Siren, label: 'Critical' },
  WARNING: { className: 'bg-amber-500/15 text-amber-300 ring-amber-500/40', icon: TriangleAlert, label: 'Warning' },
}

export function SeverityBadge({ severity, className = '' }) {
  const config = STYLES[severity]
  if (!config) return null
  const Icon = config.icon
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-medium ring-1 ring-inset ${config.className} ${className}`}
    >
      <Icon className="h-3 w-3" aria-hidden="true" />
      {config.label}
    </span>
  )
}
