import { Shield, User } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Badge } from '../components/common/Badge'
import { PageHeader } from '../components/common/PageHeader'
import { tokenStorage } from '../utils/tokenStorage'

export function SettingsPage() {
  const user = tokenStorage.getUser()

  return (
    <div className="space-y-6">
      <PageHeader
        title="Settings & Principal Profile"
        description="Active session context, environment details, and console preferences."
        breadcrumbs={[{ label: 'Settings' }]}
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <User className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
              Authenticated Principal
            </CardTitle>
            <CardDescription>
              Retrieved from <code>GET /api/v1/auth/me</code> and stored JWT session.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs">
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Username</span>
              <span className="font-semibold text-slate-900 dark:text-slate-100">{user?.username || 'admin'}</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Assigned Role</span>
              <Badge variant="accent">{user?.role || 'ADMIN'}</Badge>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Account Status</span>
              <Badge variant="success">ACTIVE</Badge>
            </div>
            <div className="flex justify-between py-1.5">
              <span className="text-slate-500 dark:text-slate-400">Password Policy</span>
              <span className="text-slate-600 dark:text-slate-300">BCrypt Hashed (Admin Provisioned)</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Shield className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
              Console & Environment Configuration
            </CardTitle>
            <CardDescription>
              Spring Boot backend integration and proxy parameters.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs">
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Vite Dev Server</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">http://localhost:3000</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Spring Boot API Target</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">http://localhost:8080</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Actuator Proxy</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">/actuator → :8080</span>
            </div>
            <div className="flex justify-between py-1.5">
              <span className="text-slate-500 dark:text-slate-400">Token Storage</span>
              <span className="font-mono text-indigo-700 dark:text-indigo-400 font-medium">Client LocalStorage</span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
