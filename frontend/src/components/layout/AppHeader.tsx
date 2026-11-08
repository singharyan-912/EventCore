import { useLocation, useNavigate } from 'react-router-dom'
import {
  Menu,
  Sun,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  User,
  Settings,
  LogOut,
  ExternalLink,
} from 'lucide-react'
import { Dropdown } from '../common/Dropdown'
import { Badge } from '../common/Badge'
import { useTheme } from '../../hooks/useTheme'
import { navigationConfig } from '../../routes/navConfig'
import { tokenStorage } from '../../utils/tokenStorage'
import { authService } from '../../services/authService'

interface AppHeaderProps {
  onToggleMobileSidebar: () => void
  isDesktopCollapsed: boolean
  onToggleDesktopCollapse: () => void
}

export function AppHeader({
  onToggleMobileSidebar,
  isDesktopCollapsed,
  onToggleDesktopCollapse,
}: AppHeaderProps) {
  const { theme, toggleTheme } = useTheme()
  const location = useLocation()
  const navigate = useNavigate()

  // Derive current page title from navConfig
  const allNavItems = navigationConfig.flatMap((section) => section.items)
  const currentItem = allNavItems.find(
    (item) => item.path === location.pathname || (item.path !== '/' && location.pathname.startsWith(item.path))
  )
  const pageTitle = currentItem?.label || 'ReliableFlow Console'

  // Retrieve authenticated principal session if present
  const sessionUser = tokenStorage.getUser()
  const username = sessionUser?.username || 'admin'
  const role = sessionUser?.role || 'ADMIN'
  const initials = username.slice(0, 2).toUpperCase()

  const handleLogout = async () => {
    await authService.logout()
    navigate('/login')
  }

  const profileMenuItems = [
    {
      id: 'profile-header',
      label: `${username} (${role})`,
      icon: <User className="h-3.5 w-3.5 text-indigo-500" />,
      onClick: () => navigate('/settings'),
    },
    {
      id: 'divider-1',
      label: '',
      divider: true,
    },
    {
      id: 'settings',
      label: 'Console Settings',
      icon: <Settings className="h-3.5 w-3.5 text-slate-400" />,
      onClick: () => navigate('/settings'),
    },
    {
      id: 'documentation',
      label: 'Swagger / Actuator (:8080)',
      icon: <ExternalLink className="h-3.5 w-3.5 text-slate-400" />,
      onClick: () => window.open('/actuator/health', '_blank'),
    },
    {
      id: 'divider-2',
      label: '',
      divider: true,
    },
    {
      id: 'logout',
      label: 'Sign Out',
      icon: <LogOut className="h-3.5 w-3.5 text-rose-500" />,
      danger: true,
      onClick: handleLogout,
    },
  ]

  return (
    <header className="sticky top-0 z-30 flex h-16 w-full items-center justify-between border-b border-slate-200 bg-white/95 px-4 backdrop-blur-xs dark:border-slate-800 dark:bg-slate-900/95 lg:px-6">
      {/* Left side: Triggers & Dynamic Title */}
      <div className="flex items-center gap-3">
        {/* Mobile menu trigger */}
        <button
          onClick={onToggleMobileSidebar}
          className="rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200 lg:hidden cursor-pointer"
          aria-label="Toggle mobile menu"
        >
          <Menu className="h-5 w-5" />
        </button>

        {/* Desktop collapse trigger */}
        <button
          onClick={onToggleDesktopCollapse}
          className="hidden lg:flex rounded-md p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200 cursor-pointer"
          title={isDesktopCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {isDesktopCollapsed ? (
            <PanelLeftOpen className="h-4 w-4" />
          ) : (
            <PanelLeftClose className="h-4 w-4" />
          )}
        </button>

        {/* Current page header title */}
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-slate-900 dark:text-slate-100">
            {pageTitle}
          </span>
          <span className="hidden sm:inline text-xs text-slate-400 dark:text-slate-600">/</span>
          <span className="hidden sm:inline text-xs font-mono text-slate-500 dark:text-slate-400">
            ReliableFlow
          </span>
        </div>
      </div>

      {/* Right side: Backend Status, Theme Toggle, User Profile */}
      <div className="flex items-center gap-2.5">
        {/* Backend connection status pill */}
        <div className="hidden sm:flex items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs text-slate-600 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-400">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
          <span className="text-[11px] font-medium">Backend Ready</span>
          <span className="text-[10px] font-mono text-slate-400 dark:text-slate-500">(:8080)</span>
        </div>

        {/* Theme Toggle (Light / Dark) */}
        <button
          onClick={toggleTheme}
          className="flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 bg-white text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:border-slate-800 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700 dark:hover:text-slate-100 transition-colors cursor-pointer shadow-2xs"
          title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
          aria-label="Toggle theme"
        >
          {theme === 'light' ? (
            <Moon className="h-4 w-4 text-slate-600" />
          ) : (
            <Sun className="h-4 w-4 text-amber-400" />
          )}
        </button>

        {/* User profile with initials avatar & dropdown */}
        <Dropdown
          align="right"
          trigger={
            <div className="flex items-center gap-2 pl-1 group">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-indigo-600 text-[11px] font-bold text-white shadow-2xs ring-2 ring-indigo-600/20 group-hover:ring-indigo-600/40 transition-all dark:bg-indigo-500">
                {initials}
              </div>
              <div className="hidden md:flex flex-col text-left">
                <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 leading-tight">
                  {username}
                </span>
                <span className="text-[10px] font-mono text-slate-500 dark:text-slate-400">
                  {role}
                </span>
              </div>
              <Badge variant="accent" className="hidden lg:inline-flex text-[10px] ml-0.5">
                {role}
              </Badge>
            </div>
          }
          items={profileMenuItems}
        />
      </div>
    </header>
  )
}
