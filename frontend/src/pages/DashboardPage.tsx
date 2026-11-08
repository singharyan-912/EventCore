import { useNavigate } from 'react-router-dom'
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Database,
  Layers,
  Send,
  ArrowUpRight,
  RefreshCw,
} from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Badge } from '../components/common/Badge'
import { Button } from '../components/common/Button'
import { PageHeader } from '../components/common/PageHeader'

export function DashboardPage() {
  const navigate = useNavigate()

  return (
    <div className="space-y-6">
      <PageHeader
        title="Dashboard"
        description="System overview of event processing platform health, transactional outbox status, and resilience pipelines."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => window.location.reload()}>
              <RefreshCw className="h-3.5 w-3.5 mr-1 text-slate-500" />
              Refresh
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate('/events')}
            >
              Explore Events
              <ArrowUpRight className="h-3.5 w-3.5 ml-1" />
            </Button>
          </div>
        }
      />

      {/* Primary KPI Grid */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Metric 1: Ingestion & Idempotency */}
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Processed Events</span>
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-50 text-indigo-600 border border-indigo-100 dark:bg-indigo-950/60 dark:text-indigo-400 dark:border-indigo-900">
                <Layers className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2">
              <span className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100 font-mono">
                100%
              </span>
              <span className="ml-2 text-xs font-medium text-emerald-600 dark:text-emerald-400">Effectively-Once</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
              <span>SHA-256 Idempotency</span>
              <span className="font-mono font-medium text-slate-700 dark:text-slate-300">Active</span>
            </div>
          </CardContent>
        </Card>

        {/* Metric 2: Transactional Outbox */}
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Transactional Outbox</span>
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-emerald-50 text-emerald-600 border border-emerald-100 dark:bg-emerald-950/60 dark:text-emerald-400 dark:border-emerald-900">
                <Send className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2">
              <span className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100 font-mono">
                ACID
              </span>
              <span className="ml-2 text-xs font-medium text-emerald-600 dark:text-emerald-400">Guaranteed</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
              <span>Kafka Topic</span>
              <span className="font-mono font-medium text-slate-700 dark:text-slate-300">inventory-events</span>
            </div>
          </CardContent>
        </Card>

        {/* Metric 3: Retry & Backoff Pipeline */}
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Scheduled Retry Worker</span>
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-amber-50 text-amber-600 border border-amber-100 dark:bg-amber-950/60 dark:text-amber-400 dark:border-amber-900">
                <Activity className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2">
              <span className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100 font-mono">
                SKIP LOCKED
              </span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
              <span>PostgreSQL Concurrency</span>
              <span className="font-mono font-medium text-slate-700 dark:text-slate-300">Non-blocking</span>
            </div>
          </CardContent>
        </Card>

        {/* Metric 4: Dead-Letter Queue */}
        <Card>
          <CardContent className="p-5">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500 dark:text-slate-400">Dead-Letter Queue (DLQ)</span>
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-rose-50 text-rose-600 border border-rose-100 dark:bg-rose-950/60 dark:text-rose-400 dark:border-rose-900">
                <AlertTriangle className="h-4 w-4" />
              </div>
            </div>
            <div className="mt-2">
              <span className="text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100 font-mono">
                0
              </span>
              <span className="ml-2 text-xs font-medium text-slate-400 dark:text-slate-500">Exhausted</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800 pt-2">
              <span>Operator Reprocessing</span>
              <span className="font-mono font-medium text-emerald-600 dark:text-emerald-400">Ready</span>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Subsystems & Operational Health Status */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Subsystem Health Matrix */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>Subsystem Health Matrix</CardTitle>
                <CardDescription>
                  Spring Boot Actuator components and background workers status.
                </CardDescription>
              </div>
              <Badge variant="success" dot>
                All Systems Operational
              </Badge>
            </div>
          </CardHeader>
          <CardContent className="space-y-3">
            {/* Component 1: PostgreSQL */}
            <div className="flex items-center justify-between rounded-md border border-slate-100 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-800/40">
              <div className="flex items-center gap-3">
                <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-slate-700 border border-slate-200 dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700">
                  <Database className="h-4 w-4" />
                </div>
                <div>
                  <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 block">
                    PostgreSQL Persistence
                  </span>
                  <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                    Flyway v6 (users, processed_events, outbox_events)
                  </span>
                </div>
              </div>
              <Badge variant="success">UP</Badge>
            </div>

            {/* Component 2: Apache Kafka Broker */}
            <div className="flex items-center justify-between rounded-md border border-slate-100 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-800/40">
              <div className="flex items-center gap-3">
                <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-slate-700 border border-slate-200 dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700">
                  <Send className="h-4 w-4" />
                </div>
                <div>
                  <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 block">
                    Apache Kafka Streaming
                  </span>
                  <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                    Broker cluster reachability & partition discovery
                  </span>
                </div>
              </div>
              <Badge variant="success">UP</Badge>
            </div>

            {/* Component 3: Outbox Publisher Worker */}
            <div className="flex items-center justify-between rounded-md border border-slate-100 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-800/40">
              <div className="flex items-center gap-3">
                <div className="flex h-8 w-8 items-center justify-center rounded bg-white text-slate-700 border border-slate-200 dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700">
                  <Activity className="h-4 w-4" />
                </div>
                <div>
                  <span className="text-xs font-semibold text-slate-900 dark:text-slate-100 block">
                    Transactional Outbox Worker
                  </span>
                  <span className="text-[11px] text-slate-500 dark:text-slate-400 font-mono">
                    SKIP LOCKED batch publisher (2000ms poll)
                  </span>
                </div>
              </div>
              <Badge variant="success">UP</Badge>
            </div>

            {/* Component 4: Retry Worker */}
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
                    Queue consumer & dead-letter transitions
                  </span>
                </div>
              </div>
              <Badge variant="success">UP</Badge>
            </div>
          </CardContent>
        </Card>

        {/* Security & Access Overview */}
        <Card>
          <CardHeader>
            <CardTitle>Role-Based Access (RBAC)</CardTitle>
            <CardDescription>
              Backend security context and active permissions.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3.5">
            <div className="flex items-start gap-2.5">
              <CheckCircle2 className="h-4 w-4 text-indigo-600 dark:text-indigo-400 mt-0.5 shrink-0" />
              <div>
                <span className="text-xs font-semibold text-slate-900 dark:text-slate-100">ADMIN</span>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  Full control: User provisioning, role assignment, security audit records, and DLQ recovery.
                </p>
              </div>
            </div>

            <div className="flex items-start gap-2.5">
              <CheckCircle2 className="h-4 w-4 text-blue-600 dark:text-blue-400 mt-0.5 shrink-0" />
              <div>
                <span className="text-xs font-semibold text-slate-900 dark:text-slate-100">OPERATOR</span>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  Operations inspection, retries, dead-letter reprocessing, outbox monitoring.
                </p>
              </div>
            </div>

            <div className="flex items-start gap-2.5">
              <CheckCircle2 className="h-4 w-4 text-slate-500 dark:text-slate-400 mt-0.5 shrink-0" />
              <div>
                <span className="text-xs font-semibold text-slate-900 dark:text-slate-100">VIEWER</span>
                <p className="text-[11px] text-slate-500 dark:text-slate-400">
                  Read-only event search, summary counts, and unified operational metrics.
                </p>
              </div>
            </div>

            <div className="rounded-md bg-slate-50 border border-slate-200 p-3 text-[11px] text-slate-600 space-y-1 dark:bg-slate-800/50 dark:border-slate-800 dark:text-slate-400">
              <span className="font-semibold block text-slate-800 dark:text-slate-200">Authentication Protocol:</span>
              <p>Stateless Bearer JWT tokens signed via HMAC-SHA256 with 1-hour validity window.</p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

