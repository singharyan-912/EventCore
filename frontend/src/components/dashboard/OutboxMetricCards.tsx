import { Send, Clock, AlertCircle, RefreshCw } from 'lucide-react'
import { Card, CardContent } from '../common/Card'
import type { OutboxSummaryResponse } from '../../types/outbox'

interface OutboxMetricCardsProps {
  outbox?: OutboxSummaryResponse
  jvmPublished?: number
  jvmFailed?: number
  isLoading?: boolean
}

export function OutboxMetricCards({
  outbox,
  jvmPublished = 0,
  jvmFailed = 0,
  isLoading = false,
}: OutboxMetricCardsProps) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {/* Outbox 1: Published */}
      <Card>
        <CardContent className="p-5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Outbox Published</span>
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-emerald-50 text-emerald-600 border border-emerald-100 dark:bg-emerald-950/60 dark:text-emerald-400 dark:border-emerald-900">
              <Send className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-2 flex items-baseline">
            <span className="text-2xl font-bold tracking-tight text-emerald-600 dark:text-emerald-400 font-mono">
              {isLoading ? '...' : (outbox?.publishedCount ?? 0).toLocaleString()}
            </span>
            <span className="ml-2 text-[11px] font-medium text-slate-500 dark:text-slate-400">Dispatched</span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
            <span>JVM Dispatched:</span>
            <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
              {isLoading ? '...' : jvmPublished.toLocaleString()}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Outbox 2: Pending In-Queue */}
      <Card>
        <CardContent className="p-5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Pending Outbox</span>
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-50 text-indigo-600 border border-indigo-100 dark:bg-indigo-950/60 dark:text-indigo-400 dark:border-indigo-900">
              <Clock className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-2 flex items-baseline">
            <span className="text-2xl font-bold tracking-tight text-indigo-600 dark:text-indigo-400 font-mono">
              {isLoading ? '...' : (outbox?.pendingCount ?? 0).toLocaleString()}
            </span>
            <span className="ml-2 text-[11px] font-medium text-slate-500 dark:text-slate-400">Awaiting Batch</span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
            <span>Claimed In-Flight:</span>
            <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
              {isLoading ? '...' : (outbox?.claimedCount ?? 0).toLocaleString()}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Outbox 3: Failed Outbox Publications */}
      <Card>
        <CardContent className="p-5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Failed Outbox</span>
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-rose-50 text-rose-600 border border-rose-100 dark:bg-rose-950/60 dark:text-rose-400 dark:border-rose-900">
              <AlertCircle className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-2 flex items-baseline">
            <span className="text-2xl font-bold tracking-tight text-rose-600 dark:text-rose-400 font-mono">
              {isLoading ? '...' : (outbox?.failedCount ?? 0).toLocaleString()}
            </span>
            <span className="ml-2 text-[11px] font-medium text-slate-500 dark:text-slate-400">Broker Failures</span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
            <span>JVM Failures:</span>
            <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
              {isLoading ? '...' : jvmFailed.toLocaleString()}
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Outbox 4: Retried Outbox Records */}
      <Card>
        <CardContent className="p-5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Retried Outbox Entries</span>
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-amber-50 text-amber-600 border border-amber-100 dark:bg-amber-950/60 dark:text-amber-400 dark:border-amber-900">
              <RefreshCw className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-2 flex items-baseline">
            <span className="text-2xl font-bold tracking-tight text-amber-600 dark:text-amber-400 font-mono">
              {isLoading ? '...' : (outbox?.retriedCount ?? 0).toLocaleString()}
            </span>
            <span className="ml-2 text-[11px] font-medium text-slate-500 dark:text-slate-400">Retry Attempts</span>
          </div>
          <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
            <span>Total Outbox Records:</span>
            <span className="font-mono font-medium text-slate-700 dark:text-slate-300">
              {isLoading ? '...' : (outbox?.totalOutboxEvents ?? 0).toLocaleString()}
            </span>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

