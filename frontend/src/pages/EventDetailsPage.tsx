import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Layers, Send, AlertTriangle, RefreshCw, Clock } from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { useEventDetail } from '../hooks/useEvents'
import type { EventStatus } from '../types/events'

export function EventDetailsPage() {
  const { key } = useParams<{ key: string }>()
  const navigate = useNavigate()
  const displayKey = key || ''

  const { data: event, isLoading, isError, error, refetch, isFetching } = useEventDetail(displayKey)

  const getStatusBadge = (status?: EventStatus | string) => {
    switch (status) {
      case 'PROCESSED':
        return <Badge variant="success" dot>PROCESSED</Badge>
      case 'RETRY_PENDING':
        return <Badge variant="warning" dot>RETRY PENDING</Badge>
      case 'DEAD_LETTER':
        return <Badge variant="error" dot>DEAD LETTER</Badge>
      case 'PROCESSING':
        return <Badge variant="info" dot>PROCESSING</Badge>
      default:
        return <Badge variant="neutral">{status || 'UNKNOWN'}</Badge>
    }
  }

  const getOutboxBadge = (status?: string) => {
    switch (status) {
      case 'PUBLISHED':
        return <Badge variant="success">PUBLISHED</Badge>
      case 'PENDING':
        return <Badge variant="accent">PENDING</Badge>
      case 'CLAIMED':
        return <Badge variant="info">CLAIMED</Badge>
      case 'FAILED':
        return <Badge variant="error">FAILED</Badge>
      default:
        return <Badge variant="neutral">{status || 'NONE'}</Badge>
    }
  }

  const formatJson = (payload?: string | null) => {
    if (!payload) return '—'
    try {
      const parsed = JSON.parse(payload)
      return JSON.stringify(parsed, null, 2)
    } catch {
      return payload
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={`Event: ${displayKey}`}
        description="Authoritative state from PostgreSQL processed_events and correlated outbox publication."
        breadcrumbs={[
          { label: 'Event Explorer', to: '/events' },
          { label: displayKey },
        ]}
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => refetch()}
              disabled={isFetching}
            >
              <RefreshCw className={`h-3.5 w-3.5 mr-1 text-slate-500 ${isFetching ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
            <Button variant="outline" size="sm" onClick={() => navigate('/events')}>
              <ArrowLeft className="h-3.5 w-3.5 mr-1" />
              Back to Explorer
            </Button>
          </div>
        }
      />

      {/* Loading State */}
      {isLoading && (
        <div className="rounded-lg border border-slate-200 bg-white p-12 text-center text-xs text-slate-500 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-400">
          Fetching event details from GET /api/v1/operations/events/{displayKey}...
        </div>
      )}

      {/* Error State */}
      {isError && (
        <div className="rounded-lg border border-rose-200 bg-rose-50 p-6 text-xs text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-200 space-y-2">
          <div className="flex items-center gap-2 font-semibold">
            <AlertTriangle className="h-4 w-4 text-rose-600" />
            <span>Failed to Load Event Detail</span>
          </div>
          <p className="text-[11px] text-rose-700 dark:text-rose-300">
            {(error as { message?: string })?.message || `Event with key '${displayKey}' could not be found or backend is unreachable.`}
          </p>
          <Button variant="outline" size="sm" onClick={() => navigate('/events')} className="mt-2">
            Return to Event Explorer
          </Button>
        </div>
      )}

      {/* Event Details Loaded */}
      {event && (
        <div className="space-y-6">
          {/* Failure reason callout if present */}
          {event.lastFailureReason && (
            <div className="flex items-start gap-2.5 rounded-md border border-rose-200 bg-rose-50 p-4 text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-200">
              <AlertTriangle className="h-4 w-4 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
              <div className="space-y-1">
                <span className="font-semibold block text-xs">Processing Failure Detected:</span>
                <p className="font-mono text-[11px] break-all">{event.lastFailureReason}</p>
              </div>
            </div>
          )}

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {/* Left Card: Event Processing Metadata */}
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="flex items-center gap-2">
                    <Layers className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                    Event Metadata
                  </CardTitle>
                  {getStatusBadge(event.status)}
                </div>
                <CardDescription>
                  Backend endpoint: <code>GET /api/v1/operations/events/{'{idempotencyKey}'}</code>
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-xs">
                <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                  <span className="text-slate-500 dark:text-slate-400">Idempotency Key</span>
                  <span className="font-mono font-medium text-slate-900 dark:text-slate-100 select-all">
                    {event.idempotencyKey}
                  </span>
                </div>
                <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                  <span className="text-slate-500 dark:text-slate-400">Event Type</span>
                  <span className="font-semibold text-slate-800 dark:text-slate-200">{event.eventType}</span>
                </div>
                <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                  <span className="text-slate-500 dark:text-slate-400">Resource ID</span>
                  <span className="font-mono text-slate-800 dark:text-slate-200">{event.resourceId}</span>
                </div>
                <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                  <span className="text-slate-500 dark:text-slate-400">HTTP Response Code</span>
                  <span className="font-mono">{event.responseStatusCode ?? '—'}</span>
                </div>
                <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                  <span className="text-slate-500 dark:text-slate-400">Retry Count / Max</span>
                  <span className="font-mono">{event.retryCount} / {event.maxRetries}</span>
                </div>
                <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                  <span className="text-slate-500 dark:text-slate-400">Next Scheduled Retry</span>
                  <span className="font-mono text-slate-700 dark:text-slate-300">
                    {event.nextRetryAt ? new Date(event.nextRetryAt).toLocaleString() : 'None'}
                  </span>
                </div>
                <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                  <span className="text-slate-500 dark:text-slate-400">Last Attempt At</span>
                  <span className="font-mono">
                    {event.lastAttemptAt ? new Date(event.lastAttemptAt).toLocaleString() : '—'}
                  </span>
                </div>
                <div className="flex justify-between py-1.5">
                  <span className="text-slate-500 dark:text-slate-400">Created At</span>
                  <span className="font-mono">{new Date(event.createdAt).toLocaleString()}</span>
                </div>
              </CardContent>
            </Card>

            {/* Right Card: Correlated Outbox Publication */}
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle className="flex items-center gap-2">
                    <Send className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                    Correlated Outbox Publication
                  </CardTitle>
                  {getOutboxBadge(event.outbox?.status)}
                </div>
                <CardDescription>
                  Transactional outbox state published to Apache Kafka.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-xs">
                {event.outbox ? (
                  <>
                    <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                      <span className="text-slate-500 dark:text-slate-400">Target Kafka Topic</span>
                      <span className="font-mono font-medium text-slate-900 dark:text-slate-100">{event.outbox.topic}</span>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                      <span className="text-slate-500 dark:text-slate-400">Partition Key</span>
                      <span className="font-mono text-slate-800 dark:text-slate-200">{event.outbox.partitionKey}</span>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                      <span className="text-slate-500 dark:text-slate-400">Outbox Retries</span>
                      <span className="font-mono">{event.outbox.retryCount} / {event.outbox.maxRetries}</span>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                      <span className="text-slate-500 dark:text-slate-400">Published Timestamp</span>
                      <span className="font-mono text-emerald-600 dark:text-emerald-400">
                        {event.outbox.publishedAt ? new Date(event.outbox.publishedAt).toLocaleString() : 'Pending publication'}
                      </span>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
                      <span className="text-slate-500 dark:text-slate-400">Outbox Record Created</span>
                      <span className="font-mono">{new Date(event.outbox.createdAt).toLocaleString()}</span>
                    </div>
                    <div className="flex justify-between py-1.5">
                      <span className="text-slate-500 dark:text-slate-400">Last Outbox Error</span>
                      <span className="font-mono text-slate-600 dark:text-slate-400">
                        {event.outbox.lastError || 'None'}
                      </span>
                    </div>
                  </>
                ) : (
                  <div className="py-12 text-center text-slate-400">
                    <Clock className="h-6 w-6 mx-auto mb-2 opacity-50" />
                    <p className="font-medium text-slate-600 dark:text-slate-300">No Correlated Outbox Entry</p>
                    <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-1">
                      This event may have experienced an execution error before an outbox message was staged.
                    </p>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Payloads Section */}
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle className="text-xs">Original Request Payload (JSON)</CardTitle>
                <CardDescription>SHA-256 idempotency hashed payload.</CardDescription>
              </CardHeader>
              <CardContent>
                <pre className="max-h-60 overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 font-mono text-[11px] text-slate-800 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200">
                  {formatJson(event.requestPayload)}
                </pre>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-xs">Cached Response Payload</CardTitle>
                <CardDescription>Payload returned upon subsequent duplicate submissions.</CardDescription>
              </CardHeader>
              <CardContent>
                <pre className="max-h-60 overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 font-mono text-[11px] text-slate-800 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200">
                  {formatJson(event.responsePayload)}
                </pre>
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}
