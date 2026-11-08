import { RefreshCw, Play, Pause, Activity } from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { SubsystemHealthCards } from '../components/monitoring/SubsystemHealthCards'
import { MetricsComparisonGrid } from '../components/monitoring/MetricsComparisonGrid'
import { MonitoringCharts } from '../components/monitoring/MonitoringCharts'
import { useMonitoring } from '../hooks/useMonitoring'

export function MonitoringPage() {
  const {
    health,
    metrics,
    isLoading,
    isFetching,
    autoRefresh,
    setAutoRefresh,
    snapshots,
    lastRefreshed,
    refreshAll,
  } = useMonitoring()

  return (
    <div className="space-y-6">
      <PageHeader
        title="System Monitoring & Observability"
        description="Spring Boot Actuator health indicators, Micrometer telemetry, and persistent database metrics."
        breadcrumbs={[{ label: 'System Monitoring' }]}
        badge={
          <Badge variant={health?.status === 'UP' ? 'success' : 'error'} dot>
            {health?.status ? `SYSTEM ${health.status}` : 'CHECKING STATUS'}
          </Badge>
        }
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setAutoRefresh(!autoRefresh)}
              className="text-xs"
            >
              {autoRefresh ? (
                <>
                  <Pause className="mr-1 h-3.5 w-3.5 text-amber-500" />
                  Pause Polling (15s)
                </>
              ) : (
                <>
                  <Play className="mr-1 h-3.5 w-3.5 text-emerald-500" />
                  Resume Polling
                </>
              )}
            </Button>

            <Button variant="outline" size="sm" onClick={refreshAll} disabled={isFetching}>
              <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
          </div>
        }
      />

      {/* Subsystem Health Cards */}
      <SubsystemHealthCards health={health} isLoading={isLoading} />

      {/* Distribution & Snapshot Charts */}
      <MonitoringCharts metrics={metrics} snapshots={snapshots} />

      {/* Dual Metrics Comparison Grid */}
      <MetricsComparisonGrid metrics={metrics} isLoading={isLoading} />

      {/* Refresh Footer */}
      <div className="flex items-center justify-between text-[11px] text-slate-400 border-t border-slate-100 pt-3 dark:border-slate-800">
        <div className="flex items-center gap-1.5">
          <Activity className="h-3.5 w-3.5 text-slate-400" />
          <span>Polled from Spring Boot Actuator and Micrometer meter registries</span>
        </div>
        <span>Last refreshed: {lastRefreshed.toLocaleTimeString()}</span>
      </div>
    </div>
  )
}
