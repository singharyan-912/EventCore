import React from 'react'
import type { Role } from '../types/auth'
import {
  LayoutDashboard,
  Layers,
  AlertTriangle,
  Send,
  Activity,
  ShieldAlert,
  Users,
  Settings,
} from 'lucide-react'

export interface NavItem {
  id: string
  label: string
  path: string
  icon: React.ComponentType<{ className?: string }>
  badge?: string
  badgeVariant?: 'neutral' | 'warning' | 'error' | 'accent' | 'info'
  description: string
  requiredRoles?: Role[]
}

export interface NavSection {
  title: string
  items: NavItem[]
}

export const navigationConfig: NavSection[] = [
  {
    title: 'Overview',
    items: [
      {
        id: 'dashboard',
        label: 'Dashboard',
        path: '/',
        icon: LayoutDashboard,
        description: 'System overview, KPIs, and operational health metrics',
      },
    ],
  },
  {
    title: 'Event Processing',
    items: [
      {
        id: 'events',
        label: 'Event Explorer',
        path: '/events',
        icon: Layers,
        description: 'Filter, inspect, and track event ingestion with idempotency hashes',
      },
      {
        id: 'dead-letters',
        label: 'Dead Letter Queue',
        path: '/dead-letters',
        icon: AlertTriangle,
        badge: 'DLQ',
        badgeVariant: 'warning',
        description: 'Poison event recovery, inspection, and operator reprocessing',
      },
      {
        id: 'outbox',
        label: 'Outbox Monitor',
        path: '/outbox',
        icon: Send,
        description: 'Transactional outbox publishing and Apache Kafka dispatch status',
      },
    ],
  },
  {
    title: 'Operations',
    items: [
      {
        id: 'monitoring',
        label: 'System Monitoring',
        path: '/monitoring',
        icon: Activity,
        description: 'Actuator health indicators, Micrometer telemetry, and JVM runtime',
      },
      {
        id: 'audit-logs',
        label: 'Audit Logs',
        path: '/audit-logs',
        icon: ShieldAlert,
        badge: 'Admin',
        badgeVariant: 'error',
        requiredRoles: ['ADMIN'],
        description: 'Immutable security lifecycle and operational recovery audit records',
      },
    ],
  },
  {
    title: 'Administration',
    items: [
      {
        id: 'users',
        label: 'User Management',
        path: '/users',
        icon: Users,
        badge: 'Admin',
        badgeVariant: 'error',
        requiredRoles: ['ADMIN'],
        description: 'Console user provisioning, RBAC roles, and status management',
      },
      {
        id: 'settings',
        label: 'Settings',
        path: '/settings',
        icon: Settings,
        description: 'Application environment preferences, proxy targets, and profile',
      },
    ],
  },
]

