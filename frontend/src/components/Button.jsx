import { Spinner } from './Spinner'

const VARIANTS = {
  primary: 'bg-emerald-500 text-emerald-950 hover:bg-emerald-400 font-semibold',
  secondary: 'border border-line bg-panel-raised text-zinc-100 hover:bg-zinc-800',
  danger: 'bg-red-500/90 text-white hover:bg-red-500 font-semibold',
  ghost: 'text-muted hover:bg-zinc-800/70 hover:text-zinc-100',
}

const SIZES = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-9 px-4 text-sm',
}

export function Button({
  variant = 'secondary',
  size = 'md',
  type = 'button',
  isLoading = false,
  disabled = false,
  icon: Icon,
  className = '',
  children,
  ...rest
}) {
  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      className={`inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
      {...rest}
    >
      {isLoading ? <Spinner size="sm" /> : Icon ? <Icon className="h-4 w-4" aria-hidden="true" /> : null}
      {children}
    </button>
  )
}
