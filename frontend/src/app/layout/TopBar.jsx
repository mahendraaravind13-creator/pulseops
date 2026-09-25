import { Menu } from 'lucide-react'
import { NotificationBell } from '../../features/notifications/NotificationBell'
import { useAuth } from '../../hooks/useAuth'
import { UserMenu } from './UserMenu'

export function TopBar({ onOpenMenu }) {
  const { tenant } = useAuth()
  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-line bg-canvas/90 px-4 backdrop-blur sm:px-6 lg:px-8">
      <button
        type="button"
        onClick={onOpenMenu}
        className="rounded-md p-1.5 text-muted hover:bg-zinc-800 hover:text-zinc-100 lg:hidden"
        aria-label="Open navigation"
      >
        <Menu className="h-5 w-5" aria-hidden="true" />
      </button>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-zinc-100">{tenant?.name}</p>
      </div>
      <NotificationBell />
      <UserMenu />
    </header>
  )
}
