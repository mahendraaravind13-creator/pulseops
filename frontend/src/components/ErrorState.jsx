import { CircleAlert, RefreshCw } from 'lucide-react'
import { getErrorMessage } from '../lib/errors'
import { Button } from './Button'

export function ErrorState({ error, onRetry, title = 'Could not load data', className = '' }) {
  return (
    <div
      role="alert"
      className={`flex flex-col items-center justify-center gap-2 px-6 py-10 text-center ${className}`}
    >
      <CircleAlert className="h-8 w-8 text-red-400" aria-hidden="true" />
      <p className="text-sm font-medium text-zinc-200">{title}</p>
      <p className="max-w-sm text-sm text-muted">{getErrorMessage(error)}</p>
      {onRetry && (
        <Button size="sm" icon={RefreshCw} onClick={() => onRetry()} className="mt-2">
          Retry
        </Button>
      )}
    </div>
  )
}
