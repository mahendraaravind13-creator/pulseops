import { useEffect } from 'react'
import { NavLink } from 'react-router-dom'
import { BellRing, LayoutDashboard, Server, Settings, Siren, X } from 'lucide-react'
import { Logo } from '../../components/Logo'

const NAV_ITEMS = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/services', label: 'Services', icon: Server },
  { to: '/incidents', label: 'Incidents', icon: Siren },
  { to: '/rules', label: 'Alert rules', icon: BellRing },
  { to: '/settings', label: 'Settings', icon: Settings },
]

function NavItems() {
  return (
    <nav aria-label="Main" className="flex flex-col gap-1 px-3">
      {NAV_ITEMS.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) =>
            `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
              isActive ? 'bg-zinc-800 text-zinc-50' : 'text-muted hover:bg-zinc-900 hover:text-zinc-100'
            }`
          }
        >
          <Icon className="h-4 w-4" aria-hidden="true" />
          {label}
        </NavLink>
      ))}
    </nav>
  )
}

export function Sidebar({ isDrawerOpen, onCloseDrawer }) {
  useEffect(() => {
    if (!isDrawerOpen) return undefined
    function handleKey(event) {
      if (event.key === 'Escape') onCloseDrawer()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [isDrawerOpen, onCloseDrawer])

  return (
    <>
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 flex-col border-r border-line bg-panel lg:flex">
        <div className="flex h-14 items-center px-6">
          <Logo />
        </div>
        <div className="mt-4">
          <NavItems />
        </div>
      </aside>

      {isDrawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
          <div className="absolute inset-0 bg-black/70" onClick={onCloseDrawer} aria-hidden="true" />
          <aside className="relative flex h-full w-64 max-w-[80vw] flex-col border-r border-line bg-panel">
            <div className="flex h-14 items-center justify-between px-5">
              <Logo />
              <button
                type="button"
                onClick={onCloseDrawer}
                className="rounded-md p-1.5 text-muted hover:bg-zinc-800 hover:text-zinc-100"
                aria-label="Close navigation"
                autoFocus
              >
                <X className="h-5 w-5" aria-hidden="true" />
              </button>
            </div>
            <div className="mt-4">
              <NavItems />
            </div>
          </aside>
        </div>
      )}
    </>
  )
}
