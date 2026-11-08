import { Server, Database, Radio, Send, RefreshCw } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardContent } from '../common/Card'
import { Badge } from '../common/Badge'
import type { ActuatorHealthResponse } from '../../types/metrics'

export interface SubsystemHealthCardsProps {
  health?: ActuatorHealthResponse
  isLoading?: boolean
}

export function SubsystemHealthCards({ health, isLoading }: SubsystemHealthCardsProps) {
  const components = health?.components

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case 'UP':
        return (
          <Badge variant="success" dot>
            UP
          </Badge>
        )
      case 'DOWN':
        return (
          <Badge variant="error" dot>
            DOWN
          </Badge>
        )
      default:
        return (
          <Badge variant="neutral" dot>
            UNKNOWN
          </Badge>
        )
    }
  }

  const cards = [
    {
      name: 'Spring Boot Application',
      subsystem: 'Core Runtime',
      icon: Server,
      status: health?.status ?? 'UNKNOWN',
      details: [
        { label: 'Probes', value: 'Liveness & Readiness Active' },
        { label: 'Actuator', value: '/actuator/health' },
      ],
    },
    {
      name: 'PostgreSQL Database',
      subsystem: 'Durable Storage & Outbox',
      icon: Database,
      status: components?.db?.status ?? 'UNKNOWN',
      details: [
        { label: 'Validation Query', value: 'HikariCP Pool Healthy' },
        { label: 'Isolation', value: 'READ_COMMITTED / Locks' },
      ],
    },
    {
      name: 'Apache Kafka Broker',
      subsystem: 'Event Streaming',
      icon: Radio,
      status: components?.kafka?.status ?? 'UNKNOWN',
      details: [
        {
          label: 'Cluster ID',
          value: (components?.kafka?.details?.clusterId as string) || 'kafka-broker',
        },
        { label: 'Timeout Guard', value: '2-second strict limit' },
      ],
    },
    {
      name: 'Transactional Outbox Publisher',
      subsystem: 'Asynchronous Relaying',
      icon: Send,
      status: components?.outbox?.status ?? 'UNKNOWN',
      details: [
        {
          label: 'Topic',
          value: (components?.outbox?.details?.topic as string) || 'inventory-events',
        },
        {
          label: 'Pending Queue',
          value: `${components?.outbox?.details?.pendingCount ?? 0} events`,
        },
      ],
    },
    {
      name: 'Background Retry Worker',
      subsystem: 'Poison Event Recovery',
      icon: RefreshCw,
      status: components?.retryWorker?.status ?? 'UNKNOWN',
      details: [
        {
          label: 'Retry Pending',
          value: `${components?.retryWorker?.details?.retryPendingCount ?? 0} events`,
        },
        {
          label: 'Poll Cycle',
          value: `${components?.retryWorker?.details?.pollIntervalMs ?? 5000} ms`,
        },
      ],
    },
  ]

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
          Subsystem Health Matrix (Actuator)
        </h3>
        <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
          <span>Overall System:</span>
          {getStatusBadge(health?.status)}
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-3.5">
        {cards.map((c) => {
          const Icon = c.icon
          return (
            <Card key={c.name} className="flex flex-col justify-between">
              <CardHeader className="p-3.5 pb-2">
                <div className="flex items-start justify-between">
                  <div className="rounded-md bg-slate-100 p-2 dark:bg-slate-800">
                    <Icon className="h-4 w-4 text-slate-700 dark:text-slate-300" />
                  </div>
                  {isLoading ? (
                    <div className="h-5 w-12 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                  ) : (
                    getStatusBadge(c.status)
                  )}
                </div>
                <CardTitle className="text-xs font-semibold mt-2.5 line-clamp-1">{c.name}</CardTitle>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">{c.subsystem}</p>
              </CardHeader>
              <CardContent className="p-3.5 pt-0">
                <div className="border-t border-slate-100 pt-2 text-[11px] space-y-1 dark:border-slate-800">
                  {c.details.map((d) => (
                    <div key={d.label} className="flex justify-between items-center text-slate-600 dark:text-slate-400">
                      <span>{d.label}:</span>
                      <span className="font-mono text-slate-900 dark:text-slate-200 truncate max-w-[120px]">
                        {d.value}
                      </span>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
