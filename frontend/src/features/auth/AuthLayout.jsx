import { Logo } from '../../components/Logo'

export function AuthLayout({ title, subtitle, children, footer, wide = false }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <Logo className="mb-8" />
      <div className={`card w-full p-6 sm:p-8 ${wide ? 'max-w-xl' : 'max-w-md'}`}>
        <h1 className="text-lg font-semibold text-zinc-50">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
        <div className="mt-6">{children}</div>
      </div>
      {footer && <div className="mt-6 text-sm text-muted">{footer}</div>}
    </div>
  )
}

export function ErrorBanner({ message }) {
  if (!message) return null
  return (
    <div role="alert" className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">
      {message}
    </div>
  )
}
