import { Inbox } from 'lucide-react'

export function EmptyState({ icon: Icon = Inbox, title, description, action, className = '' }) {
  return (
    <div className={`flex flex-col items-center justify-center gap-2 px-6 py-12 text-center ${className}`}>
      <Icon className="h-8 w-8 text-subtle" aria-hidden="true" />
      <p className="text-sm font-medium text-zinc-200">{title}</p>
      {description && <p className="max-w-sm text-sm text-muted">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}
