import { useCallback, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronDown, LogOut } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { useDismiss } from '../../hooks/useDismiss'
import { toLabel } from '../../lib/format'

function initials(name = '') {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

export function UserMenu() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [isOpen, setOpen] = useState(false)
  const containerRef = useRef(null)
  const close = useCallback(() => setOpen(false), [])
  useDismiss(containerRef, isOpen, close)

  function handleSignOut() {
    signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={() => setOpen((open) => !open)}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        className="flex items-center gap-2 rounded-lg px-1.5 py-1 hover:bg-zinc-800"
      >
        <span className="flex h-7 w-7 items-center justify-center rounded-full bg-zinc-700 text-xs font-semibold text-zinc-100">
          {initials(user?.fullName)}
        </span>
        <span className="hidden text-sm text-zinc-200 sm:inline">{user?.fullName}</span>
        <ChevronDown className="h-4 w-4 text-subtle" aria-hidden="true" />
      </button>

      {isOpen && (
        <div
          role="menu"
          className="absolute right-0 mt-2 w-60 rounded-xl border border-line bg-panel-raised p-1 shadow-2xl"
        >
          <div className="border-b border-line px-3 py-2.5">
            <p className="truncate text-sm font-medium text-zinc-100">{user?.fullName}</p>
            <p className="truncate text-xs text-muted">{user?.email}</p>
            <span className="mt-2 inline-block rounded bg-zinc-800 px-1.5 py-0.5 text-[11px] font-medium text-zinc-300">
              {toLabel(user?.role)}
            </span>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={handleSignOut}
            className="mt-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-zinc-200 hover:bg-zinc-800"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}
