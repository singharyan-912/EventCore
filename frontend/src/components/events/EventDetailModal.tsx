import { Layers, Send, Clock, AlertTriangle } from 'lucide-react'
import { Modal } from '../common/Modal'
import { Badge } from '../common/Badge'
import { Button } from '../common/Button'
import type { EventDetailResponse, EventStatus } from '../../types/events'

interface EventDetailModalProps {
  event: EventDetailResponse | null
  isOpen: boolean
  onClose: () => void
}

export function EventDetailModal({ event, isOpen, onClose }: EventDetailModalProps) {
  if (!event) return null

  const getStatusBadge = (status: EventStatus | string) => {
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
        return <Badge variant="neutral">{status}</Badge>
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
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      size="xl"
      title="Event Execution & Outbox Detail"
      description={`Authoritative event state for key: ${event.idempotencyKey}`}
      footer={
        <Button variant="primary" size="sm" onClick={onClose}>
          Close Inspection
        </Button>
      }
    >
      <div className="space-y-6 text-xs">
        {/* Top Summary Banner */}
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50/70 p-3.5 dark:border-slate-800 dark:bg-slate-800/40">
          <div>
            <span className="text-[11px] font-medium text-slate-500 dark:text-slate-400 block">Idempotency Key</span>
            <span className="font-mono text-sm font-bold text-slate-900 dark:text-slate-100 select-all">
              {event.idempotencyKey}
            </span>
          </div>
          <div className="flex items-center gap-2">
            {getStatusBadge(event.status)}
            {event.responseStatusCode && (
              <Badge variant="neutral" className="font-mono">
                HTTP {event.responseStatusCode}
              </Badge>
            )}
          </div>
        </div>

        {/* Failure reason callout if present */}
        {event.lastFailureReason && (
          <div className="flex items-start gap-2.5 rounded-md border border-rose-200 bg-rose-50 p-3 text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-200">
            <AlertTriangle className="h-4 w-4 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
            <div className="space-y-1">
              <span className="font-semibold block">Last Processing Failure:</span>
              <p className="font-mono text-[11px] break-all">{event.lastFailureReason}</p>
            </div>
          </div>
        )}

        {/* Two-Column Metadata Matrix */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {/* Column 1: Processing Metadata */}
          <div className="space-y-2 rounded-lg border border-slate-200 p-4 dark:border-slate-800">
            <h4 className="flex items-center gap-1.5 font-semibold text-slate-800 dark:text-slate-200 border-b border-slate-100 pb-2 dark:border-slate-800">
              <Layers className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
              Event Properties
            </h4>
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500 dark:text-slate-400">Event Type:</span>
                <span className="font-semibold text-slate-800 dark:text-slate-200">{event.eventType}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500 dark:text-slate-400">Resource ID:</span>
                <span className="font-mono font-medium text-slate-800 dark:text-slate-200">{event.resourceId}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500 dark:text-slate-400">Retry Counter:</span>
                <span className="font-mono">{event.retryCount} / {event.maxRetries}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500 dark:text-slate-400">Next Scheduled Retry:</span>
                <span className="font-mono text-slate-700 dark:text-slate-300">
                  {event.nextRetryAt ? new Date(event.nextRetryAt).toLocaleString() : 'None'}
                </span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-slate-500 dark:text-slate-400">Created At:</span>
                <span className="font-mono">{new Date(event.createdAt).toLocaleString()}</span>
              </div>
            </div>
          </div>

          {/* Column 2: Correlated Outbox Record */}
          <div className="space-y-2 rounded-lg border border-slate-200 p-4 dark:border-slate-800">
            <h4 className="flex items-center gap-1.5 font-semibold text-slate-800 dark:text-slate-200 border-b border-slate-100 pb-2 dark:border-slate-800">
              <Send className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
              Correlated Outbox Publication
            </h4>
            {event.outbox ? (
              <div className="divide-y divide-slate-100 dark:divide-slate-800">
                <div className="flex justify-between py-1.5">
                  <span className="text-slate-500 dark:text-slate-400">Outbox Status:</span>
                  {getOutboxBadge(event.outbox.status)}
                </div>
                <div className="flex justify-between py-1.5">
                  <span className="text-slate-500 dark:text-slate-400">Target Kafka Topic:</span>
                  <span className="font-mono font-medium text-slate-800 dark:text-slate-200">{event.outbox.topic}</span>
                </div>
                <div className="flex justify-between py-1.5">
                  <span className="text-slate-500 dark:text-slate-400">Partition Key:</span>
                  <span className="font-mono text-slate-700 dark:text-slate-300">{event.outbox.partitionKey}</span>
                </div>
                <div className="flex justify-between py-1.5">
                  <span className="text-slate-500 dark:text-slate-400">Outbox Retries:</span>
                  <span className="font-mono">{event.outbox.retryCount} / {event.outbox.maxRetries}</span>
                </div>
                <div className="flex justify-between py-1.5">
                  <span className="text-slate-500 dark:text-slate-400">Published Timestamp:</span>
                  <span className="font-mono text-emerald-600 dark:text-emerald-400">
                    {event.outbox.publishedAt ? new Date(event.outbox.publishedAt).toLocaleString() : 'Pending'}
                  </span>
                </div>
              </div>
            ) : (
              <div className="py-6 text-center text-slate-400">
                <Clock className="h-5 w-5 mx-auto mb-1 opacity-50" />
                <p>No correlated outbox record found for this idempotency key.</p>
              </div>
            )}
          </div>
        </div>

        {/* Payloads Inspection */}
        <div className="space-y-3">
          <div className="space-y-1">
            <span className="font-semibold text-slate-700 dark:text-slate-300 block">
              Event Request Payload (Normalized JSON)
            </span>
            <pre className="max-h-48 overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 font-mono text-[11px] text-slate-800 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200">
              {formatJson(event.requestPayload)}
            </pre>
          </div>

          {event.responsePayload && (
            <div className="space-y-1">
              <span className="font-semibold text-slate-700 dark:text-slate-300 block">
                Cached Response Payload (Idempotent Replay)
              </span>
              <pre className="max-h-40 overflow-auto rounded-md border border-slate-200 bg-slate-50 p-3 font-mono text-[11px] text-slate-800 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200">
                {formatJson(event.responsePayload)}
              </pre>
            </div>
          )}
        </div>
      </div>
    </Modal>
  )
}
