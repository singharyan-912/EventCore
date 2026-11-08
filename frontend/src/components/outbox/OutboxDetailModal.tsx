import { ExternalLink, Calendar, Hash, FileCode, CheckCircle, AlertTriangle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Modal } from '../common/Modal'
import { Button } from '../common/Button'
import { Badge } from '../common/Badge'
import type { EventDetailResponse } from '../../types/events'

export interface OutboxDetailModalProps {
  event: EventDetailResponse | null
  isOpen: boolean
  onClose: () => void
}

export function OutboxDetailModal({ event, isOpen, onClose }: OutboxDetailModalProps) {
  if (!event || !event.outbox) return null

  const outbox = event.outbox

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PUBLISHED':
        return <Badge variant="success" dot>PUBLISHED</Badge>
      case 'PENDING':
        return <Badge variant="warning" dot>PENDING</Badge>
      case 'CLAIMED':
        return <Badge variant="info" dot>CLAIMED</Badge>
      case 'FAILED':
        return <Badge variant="error" dot>FAILED</Badge>
      default:
        return <Badge variant="neutral">{status}</Badge>
    }
  }

  const formatPayload = (str: string | null) => {
    if (!str) return null
    try {
      return JSON.stringify(JSON.parse(str), null, 2)
    } catch {
      return str
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Outbox Record #${outbox.id}`}
      description={`Transactional outbox publication details for event ${event.idempotencyKey}`}
      size="lg"
      footer={
        <div className="flex w-full items-center justify-between">
          <Link
            to={`/events/${encodeURIComponent(event.idempotencyKey)}`}
            className="text-xs text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 inline-flex items-center gap-1 font-medium"
          >
            View in Event Explorer
            <ExternalLink className="h-3.5 w-3.5" />
          </Link>
          <Button variant="outline" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Outbox Metadata Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 rounded-lg border border-slate-200 bg-slate-50/50 p-3 text-xs dark:border-slate-800 dark:bg-slate-900/50">
          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Outbox Record ID</span>
            <span className="font-mono font-medium text-slate-900 dark:text-slate-100">
              #{outbox.id}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Publication Status</span>
            <div className="mt-0.5">{getStatusBadge(outbox.status)}</div>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Target Kafka Topic</span>
            <span className="font-mono font-medium text-indigo-600 dark:text-indigo-400">
              {outbox.topic}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Partition Key</span>
            <span className="font-mono text-slate-800 dark:text-slate-200">
              {outbox.partitionKey || 'None'}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Publish Retries</span>
            <span className="font-mono text-slate-800 dark:text-slate-200">
              {outbox.retryCount} / {outbox.maxRetries}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Correlated Event</span>
            <span className="font-mono text-slate-800 dark:text-slate-200 break-all">
              {event.eventType} ({event.resourceId})
            </span>
          </div>
        </div>

        {/* Publication Error if Failed */}
        {outbox.lastError && (
          <div className="rounded-lg border border-rose-200 bg-rose-50/60 p-3 text-xs text-rose-900 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-200">
            <div className="flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4 text-rose-600 dark:text-rose-400" />
              Kafka Producer Error
            </div>
            <div className="mt-1.5 font-mono text-[11px] bg-rose-100/60 p-2 rounded border border-rose-200/80 dark:border-rose-900 dark:bg-rose-950/60 text-rose-950 dark:text-rose-100 break-words">
              {outbox.lastError}
            </div>
          </div>
        )}

        {/* Timestamps */}
        <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500 dark:text-slate-400">
          <div className="flex items-center gap-1.5">
            <Calendar className="h-3.5 w-3.5" />
            <span>Queued: {new Date(outbox.createdAt).toLocaleString()}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <CheckCircle className="h-3.5 w-3.5" />
            <span>
              Published:{' '}
              {outbox.publishedAt ? new Date(outbox.publishedAt).toLocaleString() : 'Not yet published'}
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <Hash className="h-3.5 w-3.5" />
            <span>Updated: {new Date(outbox.updatedAt).toLocaleString()}</span>
          </div>
        </div>

        {/* Request Payload */}
        {event.requestPayload && (
          <div className="space-y-1.5">
            <span className="flex items-center gap-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300">
              <FileCode className="h-3.5 w-3.5" />
              Published Event Payload
            </span>
            <pre className="max-h-40 overflow-auto rounded-md border border-slate-200 bg-slate-50 p-2.5 font-mono text-[11px] text-slate-800 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200">
              {formatPayload(event.requestPayload)}
            </pre>
          </div>
        )}
      </div>
    </Modal>
  )
}
