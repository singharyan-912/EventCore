import { NavLink } from 'react-router-dom'
import {
  Server,
  X,
  ChevronLeft,
  ChevronRight,
  Lock,
} from 'lucide-react'
import { navigationConfig } from '../../routes/navConfig'
import { cn } from '../../utils/cn'
import { tokenStorage } from '../../utils/tokenStorage'
import type { Role } from '../../types/auth'

interface AppSidebarProps {
  isMobileOpen: boolean
  onCloseMobile: () => void
  isCollapsed: boolean
  onToggleCollapse: () => void
}

export function AppSidebar({
  isMobileOpen,
  onCloseMobile,
  isCollapsed,
  onToggleCollapse,
}: AppSidebarProps) {
  const user = tokenStorage.getUser()
  const userRole = (user?.role as Role) || 'VIEWER'

  return (
    <>
      {/* Mobile Backdrop */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-slate-900/60 backdrop-blur-xs lg:hidden transition-opacity"
          onClick={onCloseMobile}
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex flex-col border-r border-slate-200 bg-white transition-all duration-200 ease-in-out dark:border-slate-800 dark:bg-slate-900 lg:static',
          // Mobile state
          isMobileOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0',
          // Desktop collapse width
          isCollapsed ? 'lg:w-16' : 'lg:w-64',
          'w-64'
        )}
      >
        {/* Brand Header */}
        <div className="flex h-16 items-center justify-between border-b border-slate-100 px-4 dark:border-slate-800">
          <div className="flex items-center gap-2.5 overflow-hidden">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-600 text-white shadow-xs dark:bg-indigo-500">
              <Server className="h-5 w-5" />
            </div>
            {!isCollapsed && (
              <div className="truncate">
                <span className="text-sm font-bold tracking-tight text-slate-900 dark:text-slate-100 block">
                  ReliableFlow
                </span>
                <span className="block text-[10px] font-semibold uppercase tracking-wider text-indigo-600 dark:text-indigo-400">
                  Console
                </span>
              </div>
            )}
          </div>

          {/* Mobile close button */}
          <button
            onClick={onCloseMobile}
            className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800 dark:hover:text-slate-200 lg:hidden cursor-pointer"
            aria-label="Close navigation"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Navigation Sections */}
        <div className="flex-1 overflow-y-auto overflow-x-hidden px-2.5 py-4 space-y-6">
          {navigationConfig.map((section) => (
            <div key={section.title} className="space-y-1">
              {!isCollapsed ? (
                <div className="px-3 pb-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                  {section.title}
                </div>
              ) : (
                <div className="my-2 border-t border-slate-100 dark:border-slate-800" />
              )}

              <nav className="space-y-0.5">
                {section.items.map((item) => {
                  const Icon = item.icon
                  const isRestricted = Boolean(item.requiredRoles && !item.requiredRoles.includes(userRole))
                  const tooltipText = isRestricted
                    ? `${item.label} (Requires ${item.requiredRoles?.join(', ')})`
                    : item.label

                  return (
                    <NavLink
                      key={item.id}
                      to={item.path}
                      end={item.path === '/'}
                      onClick={() => {
                        if (window.innerWidth < 1024) onCloseMobile()
                      }}
                      title={isCollapsed || isRestricted ? tooltipText : undefined}
                      className={({ isActive }) =>
                        cn(
                          'flex items-center rounded-md text-xs font-medium transition-colors select-none group',
                          isCollapsed
                            ? 'h-10 w-10 justify-center mx-auto'
                            : 'px-3 py-2 justify-between',
                          isActive
                            ? 'bg-indigo-50 text-indigo-700 font-semibold shadow-2xs dark:bg-indigo-950/60 dark:text-indigo-300'
                            : isRestricted
                            ? 'text-slate-400 hover:bg-slate-100/50 dark:text-slate-500 dark:hover:bg-slate-800/50'
                            : 'text-slate-600 hover:bg-slate-100/80 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800/80 dark:hover:text-slate-200'
                        )
                      }
                    >
                      <div className="flex items-center gap-2.5 truncate">
                        <Icon className="h-4 w-4 shrink-0 transition-transform group-hover:scale-105" />
                        {!isCollapsed && (
                          <span className="truncate flex items-center gap-1.5">
                            {item.label}
                            {isRestricted && (
                              <Lock className="h-3 w-3 text-slate-400 dark:text-slate-500" />
                            )}
                          </span>
                        )}
                      </div>

                      {!isCollapsed && item.badge && (
                        <span
                          className={cn(
                            'rounded px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-tight shrink-0',
                            isRestricted
                              ? 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400'
                              : item.badgeVariant === 'warning'
                              ? 'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300'
                              : item.badgeVariant === 'error'
                              ? 'bg-rose-100 text-rose-800 dark:bg-rose-950/60 dark:text-rose-300'
                              : 'bg-indigo-100 text-indigo-800 dark:bg-indigo-950/60 dark:text-indigo-300'
                          )}
                        >
                          {isRestricted ? 'Locked' : item.badge}
                        </span>
                      )}
                    </NavLink>
                  )
                })}
              </nav>
            </div>
          ))}
        </div>

        {/* Footer info & Desktop Collapse Toggle */}
        <div className="border-t border-slate-100 p-3 bg-slate-50/70 dark:border-slate-800 dark:bg-slate-950/50">
          <div className="flex items-center justify-between">
            {!isCollapsed ? (
              <div className="flex items-center gap-2 overflow-hidden">
                <span className="relative flex h-2 w-2 shrink-0">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                </span>
                <span className="text-[11px] font-medium text-slate-600 dark:text-slate-400 truncate">
                  Backend Connected
                </span>
              </div>
            ) : (
              <div className="mx-auto flex h-2 w-2">
                <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500" title="Backend Connected" />
              </div>
            )}

            {/* Desktop collapse toggle */}
            <button
              onClick={onToggleCollapse}
              className="hidden lg:flex h-7 w-7 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200 cursor-pointer shadow-2xs"
              title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            >
              {isCollapsed ? (
                <ChevronRight className="h-3.5 w-3.5" />
              ) : (
                <ChevronLeft className="h-3.5 w-3.5" />
              )}
            </button>
          </div>
        </div>
      </aside>
    </>
  )
}
