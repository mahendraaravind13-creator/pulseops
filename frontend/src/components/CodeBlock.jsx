import { CopyButton } from './CopyButton'

export function CodeBlock({ code, label }) {
  return (
    <div className="rounded-lg border border-line bg-black/60">
      <div className="flex items-center justify-between gap-2 border-b border-line px-3 py-1.5">
        <span className="text-xs text-subtle">{label}</span>
        <CopyButton text={code} variant="ghost" />
      </div>
      <pre className="overflow-x-auto px-3 py-3 font-mono text-xs leading-relaxed text-emerald-300">
        <code>{code}</code>
      </pre>
    </div>
  )
}
