import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend,
} from 'recharts'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../common/Card'
import type { EventSummaryResponse } from '../../types/events'
import type { OutboxSummaryResponse } from '../../types/outbox'

interface DistributionChartsProps {
  events?: EventSummaryResponse
  outbox?: OutboxSummaryResponse
  isLoading?: boolean
}

// Enterprise palette matching Phase 2 design system
const EVENT_COLORS: Record<string, string> = {
  PROCESSED: '#10b981', // emerald-500
  RETRY_PENDING: '#f59e0b', // amber-500
  DEAD_LETTER: '#f43f5e', // rose-500
  PROCESSING: '#3b82f6', // blue-500
}

const OUTBOX_COLORS: Record<string, string> = {
  PUBLISHED: '#10b981', // emerald-500
  PENDING: '#6366f1', // indigo-500
  CLAIMED: '#3b82f6', // blue-500
  FAILED: '#f43f5e', // rose-500
}

export function DistributionCharts({ events, outbox, isLoading }: DistributionChartsProps) {
  // Format Event Status Data
  const eventData = [
    { name: 'PROCESSED', count: events?.processedCount ?? 0, color: EVENT_COLORS.PROCESSED },
    { name: 'RETRY_PENDING', count: events?.retryPendingCount ?? 0, color: EVENT_COLORS.RETRY_PENDING },
    { name: 'DEAD_LETTER', count: events?.deadLetterCount ?? 0, color: EVENT_COLORS.DEAD_LETTER },
    { name: 'PROCESSING', count: events?.processingCount ?? 0, color: EVENT_COLORS.PROCESSING },
  ]

  // Format Outbox Status Data
  const outboxData = [
    { name: 'PUBLISHED', count: outbox?.publishedCount ?? 0, color: OUTBOX_COLORS.PUBLISHED },
    { name: 'PENDING', count: outbox?.pendingCount ?? 0, color: OUTBOX_COLORS.PENDING },
    { name: 'CLAIMED', count: outbox?.claimedCount ?? 0, color: OUTBOX_COLORS.CLAIMED },
    { name: 'FAILED', count: outbox?.failedCount ?? 0, color: OUTBOX_COLORS.FAILED },
  ]

  const totalEventCount = events?.totalEvents ?? 0
  const processedPercent = totalEventCount > 0
    ? ((events?.processedCount ?? 0) / totalEventCount * 100).toFixed(1)
    : '100.0'

  const totalOutboxCount = outbox?.totalOutboxEvents ?? 0
  const publishedPercent = totalOutboxCount > 0
    ? ((outbox?.publishedCount ?? 0) / totalOutboxCount * 100).toFixed(1)
    : '100.0'

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      {/* Chart 1: Event Processing Status Distribution */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Event Processing Distribution</CardTitle>
              <CardDescription>
                Live distribution of events recorded in PostgreSQL processed_events.
              </CardDescription>
            </div>
            <span className="text-xs font-mono font-semibold text-emerald-600 dark:text-emerald-400">
              {processedPercent}% Processed
            </span>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="h-56 flex items-center justify-center text-xs text-slate-400">
              Loading distribution data...
            </div>
          ) : totalEventCount === 0 ? (
            <div className="h-56 flex flex-col items-center justify-center text-xs text-slate-400">
              <span>No processed events recorded yet.</span>
              <span className="text-[11px] text-slate-500 mt-1">Submit events via POST /api/v1/events to visualize.</span>
            </div>
          ) : (
            <div className="h-56 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={eventData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <XAxis
                    dataKey="name"
                    tick={{ fontSize: 10, fill: '#64748B' }}
                    tickLine={false}
                    axisLine={{ stroke: '#E2E8F0' }}
                  />
                  <YAxis
                    tick={{ fontSize: 10, fill: '#64748B' }}
                    tickLine={false}
                    axisLine={{ stroke: '#E2E8F0' }}
                    allowDecimals={false}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#FFFFFF',
                      borderColor: '#E2E8F0',
                      borderRadius: '0.375rem',
                      fontSize: '12px',
                    }}
                  />
                  <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                    {eventData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Chart 2: Transactional Outbox Distribution */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Transactional Outbox Status</CardTitle>
              <CardDescription>
                ACID outbox publication status queue in outbox_events.
              </CardDescription>
            </div>
            <span className="text-xs font-mono font-semibold text-indigo-600 dark:text-indigo-400">
              {publishedPercent}% Published
            </span>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="h-56 flex items-center justify-center text-xs text-slate-400">
              Loading outbox status...
            </div>
          ) : totalOutboxCount === 0 ? (
            <div className="h-56 flex flex-col items-center justify-center text-xs text-slate-400">
              <span>No outbox events registered yet.</span>
              <span className="text-[11px] text-slate-500 mt-1">Zero pending outbox entries in queue.</span>
            </div>
          ) : (
            <div className="h-56 w-full flex items-center justify-center">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={outboxData.filter((d) => d.count > 0)}
                    dataKey="count"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={80}
                    paddingAngle={3}
                  >
                    {outboxData.map((entry, index) => (
                      <Cell key={`pie-cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#FFFFFF',
                      borderColor: '#E2E8F0',
                      borderRadius: '0.375rem',
                      fontSize: '12px',
                    }}
                  />
                  <Legend
                    verticalAlign="bottom"
                    iconSize={8}
                    wrapperStyle={{ fontSize: '11px', paddingTop: '10px' }}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

