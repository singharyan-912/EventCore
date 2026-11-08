import { Server, Database, Activity, RefreshCw } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../common/Card'
import { Badge } from '../common/Badge'
import type { ActuatorHealthResponse } from '../../types/metrics'

interface SubsystemHealthGridProps {
  health?: ActuatorHealthResponse
  isLoading?: boolean
}

export function SubsystemHealthGrid({ health, isLoading }: SubsystemHealthGridProps) {
  const components = health?.components

  const getStatusBadge = (status?: string) => {
    if (isLoading) return <Badge variant="neutral">CHECKING...</Badge>
    const normalized = (status || 'UNKNOWN').toUpperCase()
    if (normalized === 'UP') return <Badge variant="success" dot>UP</Badge>
    if (normalized === 'DOWN') return <Badge variant="error" dot>DOWN</Badge>
    return <Badge variant="warning" dot>UNKNOWN</Badge>
  }

  const appStatus = health?.status || 'UNKNOWN'
  const dbStatus = components?.db?.status || 'UNKNOWN'
  const kafkaStatus = components?.kafka?.status || 'UNKNOWN'
  const outboxStatus = components?.outbox?.status || 'UNKNOWN'
  const retryWorkerStatus = components?.retryWorker?.status || 'UNKNOWN'

  // Extract Kafka details if present
  const kafkaDetails = components?.kafka?.details as {
    clusterId?: string
    nodes?: number
    bootstrapServers?: string
  } | undefined

  // Extract Outbox details if present
  const outboxDetails = components?.outbox?.details as {
    pollIntervalMs?: number
    topic?: string
  } | undefined

  // Extract Retry Worker details if present
  const retryDetails = components?.retryWorker?.details as {
    pollIntervalMs?: number
    batchSize?: number
  } | undefined

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>System & Subsystem Health Matrix</CardTitle>
            <CardDescription>
              Real-time telemetry reported by Spring Boot Actuator health indicators.
            </CardDescription>
          </div>
          {getStatusBadge(appStatus)}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* 1. Spring Boot Application */}
        <div className="flex items-center justify-between rounded-md border border-slate-100 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-800/40">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-slate-700 border border-slate-200 dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700">
              <Server className="h-4 w-4" />
            </div>
            <div>
              <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 block">
                Reliable Event Processor Core
              </span>
              <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                Spring Boot 3.4.3 • Java 21 LTS
              </span>
            </div>
          </div>
          {getStatusBadge(appStatus)}
        </div>

        {/* 2. PostgreSQL Persistence */}
        <div className="flex items-center justify-between rounded-md border border-slate-100 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-800/40">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-slate-700 border border-slate-200 dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700">
              <Database className="h-4 w-4" />
            </div>
            <div>
              <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 block">
                PostgreSQL Relational Persistence
              </span>
              <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                Flyway v6 • processed_events & outbox_events
              </span>
            </div>
          </div>
          {getStatusBadge(dbStatus)}
        </div>

        {/* 3. Kafka Broker Cluster */}
        <div className="flex items-center justify-between rounded-md border border-slate-100 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-800/40">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-slate-700 border border-slate-200 dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700">
              <Activity className="h-4 w-4" />
            </div>
            <div>
              <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 block">
                Apache Kafka Streaming Engine
              </span>
              <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                {kafkaDetails?.bootstrapServers
                  ? `Broker: ${kafkaDetails.bootstrapServers} (${kafkaDetails.nodes ?? 1} node)`
                  : 'Cluster discovery & consumer group tracking'}
              </span>
            </div>
          </div>
          {getStatusBadge(kafkaStatus)}
        </div>

        {/* 4. Transactional Outbox Publisher */}
        <div className="flex items-center justify-between rounded-md border border-slate-100 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-800/40">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-slate-700 border border-slate-200 dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700">
              <Activity className="h-4 w-4" />
            </div>
            <div>
              <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 block">
                Transactional Outbox Publisher
              </span>
              <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                {outboxDetails?.topic
                  ? `Topic: ${outboxDetails.topic} • Poll: ${outboxDetails.pollIntervalMs}ms`
                  : 'SKIP LOCKED batch publisher (2000ms poll)'}
              </span>
            </div>
          </div>
          {getStatusBadge(outboxStatus)}
        </div>

        {/* 5. Retry Worker */}
        <div className="flex items-center justify-between rounded-md border border-slate-100 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-800/40">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-slate-700 border border-slate-200 dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700">
              <RefreshCw className="h-4 w-4" />
            </div>
            <div>
              <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 block">
                Exponential Backoff Retry Worker
              </span>
              <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                {retryDetails?.pollIntervalMs
                  ? `Batch Size: ${retryDetails.batchSize} • Poll: ${retryDetails.pollIntervalMs}ms`
                  : 'Autonomous retry poller & dead-letter transitions'}
              </span>
            </div>
          </div>
          {getStatusBadge(retryWorkerStatus)}
        </div>
      </CardContent>
    </Card>
  )
}

