import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  User,
  Sun,
  Moon,
  LogOut,
  Lock,
  KeyRound,
  Server,
  Clock,
  Mail,
  CheckCircle2,
} from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { PageHeader } from '../components/common/PageHeader'
import { useTheme } from '../hooks/useTheme'
import { tokenStorage } from '../utils/tokenStorage'
import { authService } from '../services/authService'
import type { UserProfileResponse } from '../types/auth'

export function SettingsPage() {
  const { theme, toggleTheme } = useTheme()
  const navigate = useNavigate()
  const sessionUser = tokenStorage.getUser()
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  // Query live server-side profile verification
  const { data: profile, isLoading: loadingProfile } = useQuery<UserProfileResponse>({
    queryKey: ['auth', 'profile'],
    queryFn: authService.getProfile,
    staleTime: 30_000,
    retry: false,
  })

  const handleLogout = async () => {
    setIsLoggingOut(true)
    try {
      await authService.logout()
    } finally {
      tokenStorage.clearAuth()
      navigate('/login')
    }
  }

  const username = profile?.username || sessionUser?.username || 'admin'
  const email = profile?.email || 'admin@reliableflow.internal'
  const role = profile?.role || sessionUser?.role || 'ADMIN'
  const status = profile?.status || 'ACTIVE'

  return (
    <div className="space-y-6">
      <PageHeader
        title="Settings & Principal Profile"
        description="Active authentication session, security policy notices, and console preferences."
        breadcrumbs={[{ label: 'Settings' }]}
        actions={
          <Button
            variant="outline"
            size="sm"
            onClick={handleLogout}
            disabled={isLoggingOut}
            className="border-rose-200 text-rose-700 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-300 dark:hover:bg-rose-950/40"
          >
            <LogOut className="mr-1.5 h-3.5 w-3.5" />
            Sign Out
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* User Identity & Session Context */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <User className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                Authenticated Principal Profile
              </CardTitle>
              {loadingProfile && (
                <span className="text-[11px] text-slate-400 animate-pulse">Verifying session...</span>
              )}
            </div>
            <CardDescription>
              Verified via <code>GET /api/v1/auth/me</code> and stored stateless JWT token.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs">
            <div className="flex justify-between items-center py-2 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Username</span>
              <span className="font-semibold text-slate-900 dark:text-slate-100 font-mono">
                {username}
              </span>
            </div>

            <div className="flex justify-between items-center py-2 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400 flex items-center gap-1">
                <Mail className="h-3 w-3" />
                Email Address
              </span>
              <span className="text-slate-800 dark:text-slate-200 font-mono text-[11px]">
                {email}
              </span>
            </div>

            <div className="flex justify-between items-center py-2 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Assigned Role</span>
              <Badge variant={role === 'ADMIN' ? 'accent' : role === 'OPERATOR' ? 'warning' : 'neutral'}>
                {role}
              </Badge>
            </div>

            <div className="flex justify-between items-center py-2 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Account Status</span>
              <Badge variant={status === 'ACTIVE' ? 'success' : 'error'} dot>
                {status}
              </Badge>
            </div>

            <div className="flex justify-between items-center py-2">
              <span className="text-slate-500 dark:text-slate-400 flex items-center gap-1">
                <Clock className="h-3 w-3" />
                Session Expiration
              </span>
              <span className="font-mono text-[11px] text-slate-700 dark:text-slate-300">
                {sessionUser?.expiresAt ? new Date(sessionUser.expiresAt).toLocaleTimeString() : 'Active (Bearer)'}
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Console Appearance & Theme */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              {theme === 'dark' ? (
                <Moon className="h-4 w-4 text-indigo-400" />
              ) : (
                <Sun className="h-4 w-4 text-amber-500" />
              )}
              Appearance & Theme Preferences
            </CardTitle>
            <CardDescription>
              Customizable enterprise visual palette with persistent client storage.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 text-xs">
            <div className="flex items-center justify-between p-3 rounded-lg border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
              <div>
                <span className="font-semibold text-slate-800 dark:text-slate-200 block">
                  Color Mode: {theme === 'dark' ? 'Dark Enterprise' : 'Light Clean'}
                </span>
                <span className="text-slate-500 dark:text-slate-400 text-[11px]">
                  Persists in <code>localStorage.getItem('reliableflow_theme')</code>
                </span>
              </div>
              <Button variant="outline" size="sm" onClick={toggleTheme} className="text-xs">
                {theme === 'dark' ? (
                  <>
                    <Sun className="mr-1.5 h-3.5 w-3.5 text-amber-500" />
                    Switch to Light
                  </>
                ) : (
                  <>
                    <Moon className="mr-1.5 h-3.5 w-3.5 text-indigo-500" />
                    Switch to Dark
                  </>
                )}
              </Button>
            </div>

            <div className="space-y-2 border-t border-slate-100 pt-3 dark:border-slate-800">
              <span className="font-semibold text-slate-700 dark:text-slate-300 block">
                Design System Tokens
              </span>
              <div className="grid grid-cols-2 gap-2 text-[11px] text-slate-600 dark:text-slate-400">
                <div>Primary Accent: <code className="text-indigo-600 font-mono">#4F46E5</code></div>
                <div>Status Colors: <code className="text-emerald-600 font-mono">Tailwind tokens</code></div>
                <div>Background: <code className="font-mono">#F8FAFC / #0F172A</code></div>
                <div>Typography: <code className="font-mono">Inter / System Sans</code></div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Security Policy Notice: Contact Administrator for Password Assistance */}
        <Card className="border-amber-200 bg-amber-50/40 dark:border-amber-900/50 dark:bg-amber-950/20">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-amber-900 dark:text-amber-200">
              <Lock className="h-4 w-4 text-amber-600 dark:text-amber-400" />
              Password & Credential Policy
            </CardTitle>
            <CardDescription className="text-amber-800/80 dark:text-amber-300/80">
              Centrally administered authentication compliance
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs text-amber-900 dark:text-amber-200">
            <div className="flex items-start gap-3 rounded-lg border border-amber-200/80 bg-white/70 p-3.5 dark:border-amber-900/60 dark:bg-slate-900/80">
              <KeyRound className="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="font-semibold text-slate-900 dark:text-slate-100">
                  Password Changes Require Administrator Assistance
                </p>
                <p className="text-[11px] text-slate-600 dark:text-slate-300 leading-relaxed">
                  Self-service password resets are disabled by policy. Passwords are salted and hashed using <strong>BCrypt (strength 10)</strong> and provisioned through administrative user management.
                </p>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  If you need to rotate your credentials or have been locked out, please contact an active <strong>System Administrator</strong>.
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* System & Architecture Information */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Server className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
              Environment & Runtime Architecture
            </CardTitle>
            <CardDescription>
              ReliableFlow Console connection parameters
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-2.5 text-xs">
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Vite Application Host</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">http://localhost:3000</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Spring Boot REST Proxy</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">/api/v1 → http://localhost:8080</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Actuator Metrics Proxy</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">/actuator → http://localhost:8080</span>
            </div>
            <div className="flex justify-between py-1.5">
              <span className="text-slate-500 dark:text-slate-400">Security Architecture</span>
              <span className="text-emerald-600 dark:text-emerald-400 font-medium flex items-center gap-1">
                <CheckCircle2 className="h-3 w-3" />
                Stateless HMAC-SHA256 JWT
              </span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
