import { LoaderCircle } from 'lucide-react'

const SIZES = { sm: 'h-4 w-4', md: 'h-5 w-5', lg: 'h-8 w-8' }

export function Spinner({ size = 'md', label, className = '' }) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`} role="status">
      <LoaderCircle className={`${SIZES[size]} animate-spin`} aria-hidden="true" />
      {label ? <span className="text-sm">{label}</span> : <span className="sr-only">Loading</span>}
    </span>
  )
}

export function PageSpinner({ label = 'Loading…' }) {
  return (
    <div className="flex min-h-[40vh] items-center justify-center text-muted">
      <Spinner size="lg" label={label} />
    </div>
  )
}
