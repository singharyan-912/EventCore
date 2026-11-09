import { Link } from 'react-router-dom'
import { ShieldAlert, ArrowLeft, Lock } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardContent } from './Card'
import { Button } from './Button'
import { Badge } from './Badge'
import { tokenStorage } from '../../utils/tokenStorage'

export interface AccessDeniedProps {
  requiredRole?: string
  featureName?: string
}

export function AccessDenied({
  requiredRole = 'ADMIN',
  featureName = 'this administrative module',
}: AccessDeniedProps) {
  const user = tokenStorage.getUser()
  const userRole = user?.role || 'VIEWER'

  return (
    <div className="flex min-h-[60vh] items-center justify-center p-4">
      <Card className="max-w-md w-full border-rose-200 shadow-md dark:border-rose-900/50">
        <CardHeader className="text-center pb-2">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400">
            <ShieldAlert className="h-6 w-6" />
          </div>
          <CardTitle className="text-lg font-bold text-slate-900 dark:text-slate-100">
            Access Restricted (403)
          </CardTitle>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            Your role lacks permissions to access {featureName}.
          </p>
        </CardHeader>

        <CardContent className="space-y-4 pt-2">
          <div className="rounded-lg border border-slate-200 bg-slate-50/70 p-3 text-xs dark:border-slate-800 dark:bg-slate-900/50 space-y-2">
            <div className="flex justify-between items-center">
              <span className="text-slate-500 dark:text-slate-400">Your Current Role:</span>
              <Badge variant="neutral">{userRole}</Badge>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-slate-500 dark:text-slate-400">Required Role:</span>
              <Badge variant="error" dot>
                {requiredRole}
              </Badge>
            </div>
            <div className="flex justify-between items-center">
              <span className="text-slate-500 dark:text-slate-400">Session Status:</span>
              <span className="text-emerald-600 dark:text-emerald-400 font-medium">Active (Preserved)</span>
            </div>
          </div>

          <div className="flex items-start gap-2.5 rounded-md border border-amber-200 bg-amber-50/60 p-2.5 text-xs text-amber-900 dark:border-amber-900/40 dark:bg-amber-950/30 dark:text-amber-200">
            <Lock className="h-4 w-4 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
            <p className="text-[11px] leading-relaxed">
              Role-Based Access Control (RBAC) is enforced by the Spring Boot backend. Contact a system administrator to request elevated privileges.
            </p>
          </div>

          <div className="pt-2 flex justify-center">
            <Link to="/">
              <Button variant="primary" size="sm">
                <ArrowLeft className="mr-1.5 h-3.5 w-3.5" />
                Return to Dashboard
              </Button>
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
