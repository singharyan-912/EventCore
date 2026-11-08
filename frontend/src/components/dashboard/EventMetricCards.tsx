import { Layers, CheckCircle2, AlertTriangle, XCircle, Info } from 'lucide-react'
import { Card, CardContent } from '../common/Card'
import type { OperationsMetricsResponse } from '../../types/metrics'

interface EventMetricCardsProps {
  metrics?: OperationsMetricsResponse
  isLoading?: boolean
}

export function EventMetricCards({ metrics, isLoading }: EventMetricCardsProps) {
  const db = metrics?.databaseEvents

  return (
    <div className="space-y-3">
      {metrics?.inMemoryMetricsNotice && (
        <div className="flex items-start gap-2.5 rounded-md border border-indigo-100 bg-indigo-50/70 p-3 text-xs text-indigo-900 dark:border-indigo-900/60 dark:bg-indigo-950/40 dark:text-indigo-200">
          <Info className="h-4 w-4 shrink-0 text-indigo-600 dark:text-indigo-400 mt-0.5" />
          <div className="space-y-0.5">
            <span className="font-semibold block">Observability Dual-Layer Accounting:</span>
            <p className="text-[11px] text-indigo-700 dark:text-indigo-300">
              Primary numbers represent durable PostgreSQL totals. Sub-metrics display current JVM-lifetime processing telemetry.
            </p>
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Metric 1: Total Events */}
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Total Ingested Events</span>
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-50 text-indigo-600 border border-indigo-100 dark:bg-indigo-950/60 dark:text-indigo-400 dark:border-indigo-900">
                <Layers className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2 flex items-baseline">
              <span className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100 font-mono">
                {isLoading ? '...' : (db?.totalEvents ?? 0).toLocaleString()}
              </span>
              <span className="ml-2 text-[11px] font-medium text-slate-500 dark:text-slate-400">Durable Total</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
              <span>JVM Attempts:</span>
              <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
                {isLoading ? '...' : (metrics?.attempts ?? 0).toLocaleString()}
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Metric 2: Successfully Processed */}
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Processed Events</span>
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-emerald-50 text-emerald-600 border border-emerald-100 dark:bg-emerald-950/60 dark:text-emerald-400 dark:border-emerald-900">
                <CheckCircle2 className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2 flex items-baseline">
              <span className="text-2xl font-bold tracking-tight text-emerald-600 dark:text-emerald-400 font-mono">
                {isLoading ? '...' : (db?.processedCount ?? 0).toLocaleString()}
              </span>
              <span className="ml-2 text-[11px] font-medium text-emerald-600 dark:text-emerald-400">Effectively-Once</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
              <span>JVM Successes:</span>
              <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
                {isLoading ? '...' : (metrics?.successes ?? 0).toLocaleString()}
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Metric 3: Retry Pending */}
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Retry Pending</span>
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-amber-50 text-amber-600 border border-amber-100 dark:bg-amber-950/60 dark:text-amber-400 dark:border-amber-900">
                <AlertTriangle className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2 flex items-baseline">
              <span className="text-2xl font-bold tracking-tight text-amber-600 dark:text-amber-400 font-mono">
                {isLoading ? '...' : (db?.retryPendingCount ?? 0).toLocaleString()}
              </span>
              <span className="ml-2 text-[11px] font-medium text-amber-600 dark:text-amber-400">Backoff Queue</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
              <span>JVM Retried:</span>
              <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
                {isLoading ? '...' : (metrics?.retries ?? 0).toLocaleString()}
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Metric 4: Dead Letters */}
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Dead-Letter Queue (DLQ)</span>
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-rose-50 text-rose-600 border border-rose-100 dark:bg-rose-950/60 dark:text-rose-400 dark:border-rose-900">
                <XCircle className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2 flex items-baseline">
              <span className="text-2xl font-bold tracking-tight text-rose-600 dark:text-rose-400 font-mono">
                {isLoading ? '...' : (db?.deadLetterCount ?? 0).toLocaleString()}
              </span>
              <span className="ml-2 text-[11px] font-medium text-rose-600 dark:text-rose-400">Poison Events</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
              <span>JVM Dead-Lettered:</span>
              <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
                {isLoading ? '...' : (metrics?.deadLetters ?? 0).toLocaleString()}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

