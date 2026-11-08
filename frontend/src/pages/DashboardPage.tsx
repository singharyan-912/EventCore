import { RefreshCw, ArrowUpRight, AlertCircle } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { EventMetricCards } from '../components/dashboard/EventMetricCards'
import { OutboxMetricCards } from '../components/dashboard/OutboxMetricCards'
import { SubsystemHealthGrid } from '../components/dashboard/SubsystemHealthGrid'
import { DistributionCharts } from '../components/dashboard/DistributionCharts'
import { RecentEventsTable } from '../components/dashboard/RecentEventsTable'
import { useDashboard } from '../hooks/useDashboard'

export function DashboardPage() {
  const navigate = useNavigate()
  const {
    metrics,
    health,
    recentEvents,
    isLoading,
    isFetching,
    isError,
    errorMessage,
    lastRefreshed,
    refreshAll,
  } = useDashboard()

  const formattedTime = lastRefreshed.toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })

  return (
    <div className="space-y-6">
      {/* Page Header with Real-Time Refresh & Observability Metadata */}
      <PageHeader
        title="Operational Dashboard"
        description="Unified observability of event processing throughput, transactional outbox status, and resilience pipelines."
        actions={
          <div className="flex items-center gap-3">
            <span className="hidden sm:inline-block text-[11px] font-mono text-slate-500 dark:text-slate-400">
              Refreshed: {formattedTime}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={refreshAll}
              disabled={isFetching}
            >
              <RefreshCw className={`h-3.5 w-3.5 mr-1 text-slate-500 ${isFetching ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate('/events')}
            >
              Event Explorer
              <ArrowUpRight className="h-3.5 w-3.5 ml-1" />
            </Button>
          </div>
        }
      />

      {/* Backend Unavailability or Error Alert Banner */}
      {isError && (
        <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-xs text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
          <AlertCircle className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
          <div className="space-y-1">
            <span className="font-semibold block">Observability Data Connection Note</span>
            <p className="text-[11px] text-amber-800 dark:text-amber-300">
              {errorMessage || 'Unable to retrieve live telemetry from Spring Boot backend. Ensure backend is running on :8080.'}
            </p>
          </div>
        </div>
      )}

      {/* 1. Core Event Processing KPI Grid */}
      <div className="space-y-2">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
          Event Ingestion & Deduplication Metrics
        </h2>
        <EventMetricCards metrics={metrics} isLoading={isLoading} />
      </div>

      {/* 2. Transactional Outbox Pipeline Grid */}
      <div className="space-y-2">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
          Transactional Outbox & Kafka Dispatch Pipeline
        </h2>
        <OutboxMetricCards
          outbox={metrics?.databaseOutbox}
          jvmPublished={metrics?.outboxPublished}
          jvmFailed={metrics?.outboxFailed}
          isLoading={isLoading}
        />
      </div>

      {/* 3. Subsystem Health Matrix */}
      <SubsystemHealthGrid health={health} isLoading={isLoading} />

      {/* 4. Real Data Distribution Charts */}
      <div className="space-y-2">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
          Queue Distribution & Status Telemetry
        </h2>
        <DistributionCharts
          events={metrics?.databaseEvents}
          outbox={metrics?.databaseOutbox}
          isLoading={isLoading}
        />
      </div>

      {/* 5. Recent Ingested Events Roster */}
      <div className="space-y-2">
        <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
          Recent Pipeline Activity
        </h2>
        <RecentEventsTable events={recentEvents} isLoading={isLoading} />
      </div>
    </div>
  )
}
