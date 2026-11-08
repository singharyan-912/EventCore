import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
  LineChart,
  Line,
} from 'recharts'
import { Activity, Clock } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardContent } from '../common/Card'
import type { OperationsMetricsResponse } from '../../types/metrics'
import type { MetricSnapshot } from '../../hooks/useMonitoring'

export interface MonitoringChartsProps {
  metrics?: OperationsMetricsResponse
  snapshots: MetricSnapshot[]
}

const OUTBOX_COLORS: Record<string, string> = {
  Published: '#10B981', // emerald-500
  Pending: '#F59E0B',   // amber-500
  Failed: '#F43F5E',    // rose-500
}

export function MonitoringCharts({ metrics, snapshots }: MonitoringChartsProps) {
  const dbEvents = metrics?.databaseEvents
  const dbOutbox = metrics?.databaseOutbox

  // 1. Event Distribution Bar Data
  const eventStatusData = [
    { status: 'Processed', count: dbEvents?.processedCount ?? 0, fill: '#10B981' },
    { status: 'Retry Pending', count: dbEvents?.retryPendingCount ?? 0, fill: '#F59E0B' },
    { status: 'Dead Letter', count: dbEvents?.deadLetterCount ?? 0, fill: '#F43F5E' },
    { status: 'In Processing', count: dbEvents?.processingCount ?? 0, fill: '#3B82F6' },
  ]

  // 2. Outbox Distribution Pie Data
  const outboxPieData = [
    { name: 'Published', value: dbOutbox?.publishedCount ?? 0 },
    { name: 'Pending', value: (dbOutbox?.pendingCount ?? 0) + (dbOutbox?.claimedCount ?? 0) },
    { name: 'Failed', value: dbOutbox?.failedCount ?? 0 },
  ].filter((d) => d.value > 0)

  return (
    <div className="space-y-4">
      {/* Top Row: Event Distribution & Outbox Ratio */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Status Breakdown Bar Chart */}
        <Card>
          <CardHeader className="p-4 pb-2 border-b border-slate-100 dark:border-slate-800">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
              Durable Event Status Breakdown
            </CardTitle>
            <p className="text-[11px] text-slate-400">
              Database counts across all processed and queued records
            </p>
          </CardHeader>
          <CardContent className="p-4">
            <div className="h-60 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={eventStatusData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" opacity={0.6} />
                  <XAxis dataKey="status" tick={{ fontSize: 11, fill: '#64748B' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#64748B' }} axisLine={false} tickLine={false} allowDecimals={false} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#1E293B',
                      borderColor: '#334155',
                      borderRadius: '6px',
                      color: '#F8FAFC',
                      fontSize: '12px',
                    }}
                  />
                  <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                    {eventStatusData.map((entry, index) => (
                      <Cell key={`bar-${index}`} fill={entry.fill} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Outbox Publication Breakdown Pie Chart */}
        <Card>
          <CardHeader className="p-4 pb-2 border-b border-slate-100 dark:border-slate-800">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">
              Outbox Delivery Ratio
            </CardTitle>
            <p className="text-[11px] text-slate-400">
              Transactional outbox state distribution (PostgreSQL records)
            </p>
          </CardHeader>
          <CardContent className="p-4">
            <div className="h-60 w-full">
              {outboxPieData.length === 0 ? (
                <div className="h-full flex items-center justify-center text-xs text-slate-400 italic">
                  No outbox records currently recorded
                </div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={outboxPieData}
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={80}
                      paddingAngle={3}
                      dataKey="value"
                    >
                      {outboxPieData.map((entry) => (
                        <Cell key={`cell-${entry.name}`} fill={OUTBOX_COLORS[entry.name] || '#94A3B8'} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#1E293B',
                        borderColor: '#334155',
                        borderRadius: '6px',
                        color: '#F8FAFC',
                        fontSize: '12px',
                      }}
                    />
                    <Legend
                      formatter={(val: string) => (
                        <span className="text-xs font-medium text-slate-700 dark:text-slate-300">{val}</span>
                      )}
                    />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Session-Only Rolling Snapshot Line Chart */}
      <Card>
        <CardHeader className="p-4 pb-2 border-b border-slate-100 dark:border-slate-800">
          <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-1">
            <div className="flex items-center gap-2">
              <Activity className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                Session-Only Live Snapshot
              </CardTitle>
            </div>
            <div className="flex items-center gap-1.5 text-[11px] text-amber-600 dark:text-amber-400 font-medium">
              <Clock className="h-3 w-3" />
              <span>In-memory client telemetry, resets on browser refresh</span>
            </div>
          </div>
          <p className="text-[11px] text-slate-400">
            Continuous polling trend of runtime processing attempts, successes, and broker publications during this browser session.
          </p>
        </CardHeader>
        <CardContent className="p-4">
          <div className="h-56 w-full">
            {snapshots.length < 2 ? (
              <div className="h-full flex items-center justify-center text-xs text-slate-400 italic">
                Collecting live telemetry snapshots... (minimum 2 data points required)
              </div>
            ) : (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={snapshots} margin={{ top: 10, right: 20, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" opacity={0.6} />
                  <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#64748B' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 10, fill: '#64748B' }} axisLine={false} tickLine={false} allowDecimals={false} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#1E293B',
                      borderColor: '#334155',
                      borderRadius: '6px',
                      color: '#F8FAFC',
                      fontSize: '11px',
                    }}
                  />
                  <Legend
                    formatter={(val: string) => (
                      <span className="text-xs font-medium text-slate-700 dark:text-slate-300">{val}</span>
                    )}
                  />
                  <Line
                    type="monotone"
                    dataKey="attempts"
                    name="Ingestion Attempts"
                    stroke="#6366F1"
                    strokeWidth={2}
                    dot={{ r: 2 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="successes"
                    name="Successes"
                    stroke="#10B981"
                    strokeWidth={2}
                    dot={{ r: 2 }}
                  />
                  <Line
                    type="monotone"
                    dataKey="outboxPublished"
                    name="Outbox Published"
                    stroke="#06B6D4"
                    strokeWidth={2}
                    dot={{ r: 2 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

