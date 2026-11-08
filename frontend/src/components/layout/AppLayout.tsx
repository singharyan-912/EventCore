import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
import { AppSidebar } from './AppSidebar'
import { AppHeader } from './AppHeader'

const SIDEBAR_COLLAPSE_KEY = 'reliableflow_sidebar_collapsed'

export function AppLayout() {
  const [isMobileOpen, setIsMobileOpen] = useState(false)
  const [isDesktopCollapsed, setIsDesktopCollapsed] = useState(() => {
    try {
      return localStorage.getItem(SIDEBAR_COLLAPSE_KEY) === 'true'
    } catch {
      return false
    }
  })

  useEffect(() => {
    try {
      localStorage.setItem(SIDEBAR_COLLAPSE_KEY, String(isDesktopCollapsed))
    } catch {
      // ignore
    }
  }, [isDesktopCollapsed])

  return (
    <div className="flex min-h-screen bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100 transition-colors duration-150">
      {/* Sidebar navigation */}
      <AppSidebar
        isMobileOpen={isMobileOpen}
        onCloseMobile={() => setIsMobileOpen(false)}
        isCollapsed={isDesktopCollapsed}
        onToggleCollapse={() => setIsDesktopCollapsed((prev) => !prev)}
      />

      {/* Main Content Viewport */}
      <div className="flex flex-1 flex-col overflow-hidden min-w-0">
        <AppHeader
          onToggleMobileSidebar={() => setIsMobileOpen((prev) => !prev)}
          isDesktopCollapsed={isDesktopCollapsed}
          onToggleDesktopCollapse={() => setIsDesktopCollapsed((prev) => !prev)}
        />
        <main className="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">
          <div className="mx-auto max-w-7xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
