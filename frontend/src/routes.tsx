import { createBrowserRouter, Navigate } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import { EventExplorerPage } from './pages/EventExplorerPage'
import { EventDetailsPage } from './pages/EventDetailsPage'
import { DeadLetterPage } from './pages/DeadLetterPage'
import { OutboxPage } from './pages/OutboxPage'
import { MonitoringPage } from './pages/MonitoringPage'
import { AuditLogsPage } from './pages/AuditLogsPage'
import { UsersPage } from './pages/UsersPage'
import { SettingsPage } from './pages/SettingsPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ApiKeysPage } from './pages/ApiKeysPage'
import { IntegrationsPage } from './pages/IntegrationsPage'
import { WebhooksPage } from './pages/WebhooksPage'
import { ReconciliationPage } from './pages/ReconciliationPage'
import { ProtectedRoute } from './components/auth/ProtectedRoute'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        index: true,
        element: <DashboardPage />,
      },
      {
        path: 'events',
        element: <EventExplorerPage />,
      },
      {
        path: 'events/:key',
        element: <EventDetailsPage />,
      },
      {
        path: 'dead-letters',
        element: <DeadLetterPage />,
      },
      {
        path: 'reconciliations',
        element: <ReconciliationPage />,
      },
      {
        path: 'outbox',
        element: <OutboxPage />,
      },
      {
        path: 'monitoring',
        element: <MonitoringPage />,
      },
      {
        path: 'audit-logs',
        element: (
          <ProtectedRoute allowedRoles={['ADMIN']} featureName="Security & Operational Audit Logs">
            <AuditLogsPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'users',
        element: (
          <ProtectedRoute allowedRoles={['ADMIN']} featureName="User Management & RBAC Administration">
            <UsersPage />
          </ProtectedRoute>
        ),
      },
      {
        path: 'api-keys',
        element: <ApiKeysPage />,
      },
      {
        path: 'integrations',
        element: <IntegrationsPage />,
      },
      {
        path: 'webhooks',
        element: <WebhooksPage />,
      },
      {
        path: 'settings',
        element: <SettingsPage />,
      },
      {
        path: '*',
        element: <Navigate to="/" replace />,
      },
    ],
  },
])

