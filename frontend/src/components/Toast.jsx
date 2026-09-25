import { useCallback, useMemo, useRef, useState } from 'react'
import { CircleAlert, CircleCheck, Info, X } from 'lucide-react'
import { ToastContext } from './toastContext'

const DISMISS_AFTER_MS = 4500

const TONES = {
  success: { icon: CircleCheck, className: 'text-emerald-400' },
  error: { icon: CircleAlert, className: 'text-red-400' },
  info: { icon: Info, className: 'text-sky-400' },
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const nextId = useRef(1)

  const dismiss = useCallback((id) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const show = useCallback(
    (tone, message) => {
      const id = nextId.current++
      setToasts((current) => [...current.slice(-3), { id, tone, message }])
      setTimeout(() => dismiss(id), DISMISS_AFTER_MS)
    },
    [dismiss],
  )

  const api = useMemo(
    () => ({
      success: (message) => show('success', message),
      error: (message) => show('error', message),
      info: (message) => show('info', message),
    }),
    [show],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        className="pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex flex-col items-center gap-2 p-4 sm:items-end"
        aria-live="polite"
        role="status"
      >
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} onDismiss={() => dismiss(toast.id)} />
        ))}
      </div>
    </ToastContext.Provider>
  )
}

function ToastItem({ toast, onDismiss }) {
  const { icon: Icon, className } = TONES[toast.tone]
  return (
    <div className="pointer-events-auto flex w-full max-w-sm animate-toast-in items-start gap-3 rounded-lg border border-line bg-panel-raised px-4 py-3 shadow-xl">
      <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${className}`} aria-hidden="true" />
      <p className="flex-1 text-sm text-zinc-100">{toast.message}</p>
      <button
        type="button"
        onClick={onDismiss}
        className="rounded p-0.5 text-subtle hover:text-zinc-100"
        aria-label="Dismiss notification"
      >
        <X className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  )
}
