export function SettingsCard({ title, description, actions, children, className = '' }) {
  return (
    <section className={`card p-5 ${className}`}>
      <div className="mb-4 flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold text-zinc-100">{title}</h2>
          {description && <p className="mt-1 text-sm text-muted">{description}</p>}
        </div>
        {actions}
      </div>
      {children}
    </section>
  )
}
