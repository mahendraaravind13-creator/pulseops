import { useCallback, useEffect, useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'

export function AppShell() {
  const [isDrawerOpen, setDrawerOpen] = useState(false)
  const location = useLocation()
  const closeDrawer = useCallback(() => setDrawerOpen(false), [])

  useEffect(() => {
    setDrawerOpen(false)
  }, [location.pathname])

  return (
    <div className="min-h-screen">
      <Sidebar isDrawerOpen={isDrawerOpen} onCloseDrawer={closeDrawer} />
      <div className="lg:pl-60">
        <TopBar onOpenMenu={() => setDrawerOpen(true)} />
        <main className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
