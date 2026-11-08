import { Info, Database, Cpu } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardContent } from '../common/Card'
import type { OperationsMetricsResponse } from '../../types/metrics'

export interface MetricsComparisonGridProps {
  metrics?: OperationsMetricsResponse
  isLoading?: boolean
}

export function MetricsComparisonGrid({ metrics, isLoading }: MetricsComparisonGridProps) {
  const dbEvents = metrics?.databaseEvents
  const dbOutbox = metrics?.databaseOutbox

  const runtimeMetrics = [
    { label: 'Event Ingestion Attempts', value: metrics?.attempts ?? 0, note: 'JVM counter sum' },
    { label: 'Processing Successes', value: metrics?.successes ?? 0, note: 'HTTP 200/201' },
    { label: 'Processing Failures', value: metrics?.failures ?? 0, note: 'Initial try exceptions' },
    { label: 'Idempotent Deduplications', value: metrics?.duplicates ?? 0, note: 'Replay responses cached' },
    { label: 'Retry Loop Executions', value: metrics?.retries ?? 0, note: 'Worker attempts' },
    { label: 'Poison Dead Letters', value: metrics?.deadLetters ?? 0, note: 'Moved to DLQ' },
    { label: 'Outbox Broker Deliveries', value: metrics?.outboxPublished ?? 0, note: 'Kafka ACK received' },
    { label: 'Outbox Delivery Failures', value: metrics?.outboxFailed ?? 0, note: 'Kafka NACK / timeout' },
    { label: 'Consumer Processed', value: metrics?.consumerSuccess ?? 0, note: 'Kafka consumer ACKs' },
    { label: 'Consumer Failures', value: metrics?.consumerFailure ?? 0, note: 'Consumer listener errors' },
    { label: 'Consumer Duplicates Filtered', value: metrics?.consumerDuplicate ?? 0, note: 'At-least-once filtered' },
  ]

  const durableMetrics = [
    { label: 'Total Events in Database', value: dbEvents?.totalEvents ?? 0, note: 'processed_events table' },
    { label: 'Processed & Finalized', value: dbEvents?.processedCount ?? 0, note: 'status = PROCESSED' },
    { label: 'Retry Pending in Queue', value: dbEvents?.retryPendingCount ?? 0, note: 'status = RETRY_PENDING' },
    { label: 'Poison Events in DLQ', value: dbEvents?.deadLetterCount ?? 0, note: 'status = DEAD_LETTER' },
    { label: 'Currently In-Flight', value: dbEvents?.processingCount ?? 0, note: 'status = PROCESSING' },
    { label: 'Total Outbox Records', value: dbOutbox?.totalOutboxEvents ?? 0, note: 'outbox_events table' },
    { label: 'Outbox Published to Kafka', value: dbOutbox?.publishedCount ?? 0, note: 'status = PUBLISHED' },
    { label: 'Outbox Pending Poll', value: (dbOutbox?.pendingCount ?? 0) + (dbOutbox?.claimedCount ?? 0), note: 'PENDING / CLAIMED' },
    { label: 'Outbox Failed Publications', value: dbOutbox?.failedCount ?? 0, note: 'status = FAILED' },
    { label: 'Outbox Retried Records', value: dbOutbox?.retriedCount ?? 0, note: 'retry_count > 0' },
  ]

  return (
    <div className="space-y-4">
      {/* Backend In-Memory Notice Banner */}
      <div className="flex items-start gap-3 rounded-lg border border-indigo-200 bg-indigo-50/70 p-3.5 text-xs text-indigo-900 dark:border-indigo-900/50 dark:bg-indigo-950/40 dark:text-indigo-200">
        <Info className="h-4 w-4 shrink-0 text-indigo-600 dark:text-indigo-400 mt-0.5" />
        <div>
          <span className="font-semibold">Architectural Telemetry Separation:</span>
          <p className="mt-0.5 leading-relaxed text-indigo-800/90 dark:text-indigo-300/90">
            {metrics?.inMemoryMetricsNotice ||
              'In-memory metrics reflect the current JVM process lifetime and reset upon application restart. Use database summaries for durable historical counts.'}
          </p>
        </div>
      </div>

      {/* Two Column Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Durable Database Counts */}
        <Card>
          <CardHeader className="p-4 pb-2 border-b border-slate-100 dark:border-slate-800">
            <div className="flex items-center gap-2">
              <div className="rounded-md bg-emerald-50 p-1.5 text-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-400">
                <Database className="h-4 w-4" />
              </div>
              <div>
                <CardTitle className="text-sm font-semibold">Durable Database Totals</CardTitle>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  Authoritative, ACID persistent records queried from PostgreSQL
                </p>
              </div>
            </div>
          </CardHeader>
          <CardContent className="p-4 pt-3">
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {durableMetrics.map((item) => (
                <div key={item.label} className="py-2 flex items-center justify-between text-xs">
                  <div>
                    <span className="font-medium text-slate-700 dark:text-slate-300 block">{item.label}</span>
                    <span className="text-[11px] text-slate-400 dark:text-slate-500">{item.note}</span>
                  </div>
                  <div className="font-mono text-sm font-bold text-slate-900 dark:text-slate-100">
                    {isLoading ? (
                      <div className="h-4 w-12 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                    ) : (
                      item.value.toLocaleString()
                    )}
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Runtime In-Memory Counters */}
        <Card>
          <CardHeader className="p-4 pb-2 border-b border-slate-100 dark:border-slate-800">
            <div className="flex items-center gap-2">
              <div className="rounded-md bg-indigo-50 p-1.5 text-indigo-600 dark:bg-indigo-950/50 dark:text-indigo-400">
                <Cpu className="h-4 w-4" />
              </div>
              <div>
                <CardTitle className="text-sm font-semibold">JVM Runtime Telemetry</CardTitle>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  High-throughput Micrometer counters tracking the current process lifetime
                </p>
              </div>
            </div>
          </CardHeader>
          <CardContent className="p-4 pt-3">
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {runtimeMetrics.map((item) => (
                <div key={item.label} className="py-2 flex items-center justify-between text-xs">
                  <div>
                    <span className="font-medium text-slate-700 dark:text-slate-300 block">{item.label}</span>
                    <span className="text-[11px] text-slate-400 dark:text-slate-500">{item.note}</span>
                  </div>
                  <div className="font-mono text-sm font-bold text-indigo-600 dark:text-indigo-400">
                    {isLoading ? (
                      <div className="h-4 w-12 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                    ) : (
                      item.value.toLocaleString()
                    )}
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
