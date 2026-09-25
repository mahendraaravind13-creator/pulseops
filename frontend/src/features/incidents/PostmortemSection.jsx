import { CircleAlert, FileText, RefreshCw } from 'lucide-react'
import { Button } from '../../components/Button'
import { CopyButton } from '../../components/CopyButton'
import { Spinner } from '../../components/Spinner'

export function PostmortemSection({ analysis, onRetry, isRetrying }) {
  const status = analysis?.postmortemStatus ?? null
  const canCopy = status === 'COMPLETED' && Boolean(analysis.postmortem)

  return (
    <section className="card p-4" aria-labelledby="postmortem-title">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <FileText className="h-4 w-4 text-sky-400" aria-hidden="true" />
          <h2 id="postmortem-title" className="text-sm font-semibold text-zinc-100">
            Post-mortem draft
          </h2>
        </div>
        {canCopy && <CopyButton text={analysis.postmortem} />}
      </div>
      <PostmortemBody analysis={analysis} status={status} onRetry={onRetry} isRetrying={isRetrying} />
    </section>
  )
}

function PostmortemBody({ analysis, status, onRetry, isRetrying }) {
  if (analysis?.status === 'DISABLED') {
    return <p className="text-sm text-muted">AI analysis is not configured on this server.</p>
  }

  if (status === 'PENDING') {
    return (
      <div className="py-6 text-sky-300">
        <Spinner label="Drafting post-mortem…" />
      </div>
    )
  }

  if (status === 'COMPLETED') {
    return (
      <div className="space-y-3">
        <div className="max-h-[480px] overflow-y-auto whitespace-pre-wrap rounded-lg border border-line bg-black/40 p-4 text-sm leading-relaxed text-zinc-200">
          {analysis.postmortem}
        </div>
        <p className="text-xs text-amber-300/90">AI suggestion — verify before acting</p>
      </div>
    )
  }

  const message =
    status === 'FAILED'
      ? 'The post-mortem could not be generated.'
      : 'No post-mortem has been generated for this incident.'

  return (
    <div className="space-y-3">
      <div
        role={status === 'FAILED' ? 'alert' : undefined}
        className={`flex gap-2 text-sm ${status === 'FAILED' ? 'rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-red-300' : 'text-muted'}`}
      >
        {status === 'FAILED' && <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />}
        <span>{message}</span>
      </div>
      <Button size="sm" icon={RefreshCw} onClick={onRetry} isLoading={isRetrying}>
        {status === 'FAILED' ? 'Retry post-mortem' : 'Generate post-mortem'}
      </Button>
    </div>
  )
}
