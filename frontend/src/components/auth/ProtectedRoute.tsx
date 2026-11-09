import React from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { tokenStorage } from '../../utils/tokenStorage'
import { AccessDenied } from '../common/AccessDenied'
import type { Role } from '../../types/auth'

export interface ProtectedRouteProps {
  children: React.ReactNode
  allowedRoles?: Role[]
  featureName?: string
}

export function ProtectedRoute({
  children,
  allowedRoles,
  featureName,
}: ProtectedRouteProps) {
  const location = useLocation()
  const user = tokenStorage.getUser()
  const isAuthenticated = tokenStorage.isAuthenticated()

  if (!isAuthenticated) {
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />
  }

  if (allowedRoles && allowedRoles.length > 0) {
    const userRole = (user?.role as Role) || 'VIEWER'
    if (!allowedRoles.includes(userRole)) {
      return (
        <AccessDenied
          requiredRole={allowedRoles.join(' or ')}
          featureName={featureName}
        />
      )
    }
  }

  return <>{children}</>
}
