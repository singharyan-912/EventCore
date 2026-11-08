import { Link } from 'react-router-dom'
import { AlertCircle, RefreshCw, ExternalLink, Calendar, Hash, FileCode } from 'lucide-react'
import { Modal } from '../common/Modal'
import { Button } from '../common/Button'
import { Badge } from '../common/Badge'
import type { DeadLetterResponse } from '../../types/events'
import { useEventDetail } from '../../hooks/useEvents'

export interface DeadLetterDetailModalProps {
  event: DeadLetterResponse | null
  isOpen: boolean
  onClose: () => void
  onReprocess: (key: string) => void
  canReprocess: boolean
}

export function DeadLetterDetailModal({
  event,
  isOpen,
  onClose,
  onReprocess,
  canReprocess,
}: DeadLetterDetailModalProps) {
  // Query full event detail for original request payload
  const { data: fullDetail, isLoading: loadingPayload } = useEventDetail(
    isOpen && event ? event.idempotencyKey : undefined
  )
  const payload = fullDetail?.requestPayload ?? null

  if (!event) return null

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
      title="Dead Letter Event Details"
      description={`Failure audit and recovery options for ${event.idempotencyKey}`}
      size="lg"
      footer={
        <div className="flex w-full items-center justify-between">
          <Link
            to={`/events/${encodeURIComponent(event.idempotencyKey)}`}
            className="text-xs text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 inline-flex items-center gap-1 font-medium"
          >
            Open in Event Explorer
            <ExternalLink className="h-3.5 w-3.5" />
          </Link>

          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={onClose}>
              Close
            </Button>
            {canReprocess && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  onClose()
                  onReprocess(event.idempotencyKey)
                }}
                className="bg-amber-600 hover:bg-amber-700 text-white"
              >
                <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                Reprocess Event
              </Button>
            )}
          </div>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Header Metadata Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 rounded-lg border border-slate-200 bg-slate-50/50 p-3 text-xs dark:border-slate-800 dark:bg-slate-900/50">
          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Idempotency Key</span>
            <span className="font-mono font-medium text-slate-900 dark:text-slate-100 break-all">
              {event.idempotencyKey}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Event Type</span>
            <span className="font-mono text-slate-900 dark:text-slate-100">{event.eventType}</span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Resource ID</span>
            <span className="font-mono text-slate-900 dark:text-slate-100">{event.resourceId}</span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Status</span>
            <Badge variant="error" dot className="mt-0.5">
              DEAD_LETTER
            </Badge>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Exhausted Attempts</span>
            <span className="font-mono font-medium text-rose-600 dark:text-rose-400">
              {event.retryCount} / {event.maxRetries}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Next Retry At</span>
            <span className="font-mono text-slate-600 dark:text-slate-400">
              {event.nextRetryAt ? new Date(event.nextRetryAt).toLocaleString() : 'Suspended (DLQ)'}
            </span>
          </div>
        </div>

        {/* Failure Reason Alert */}
        <div className="rounded-lg border border-rose-200 bg-rose-50/60 p-3 text-xs text-rose-900 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-200">
          <div className="flex items-center gap-2 font-semibold">
            <AlertCircle className="h-4 w-4 text-rose-600 dark:text-rose-400" />
            Last Execution Failure Reason
          </div>
          <div className="mt-1.5 font-mono text-[11px] bg-rose-100/60 p-2.5 rounded border border-rose-200/80 dark:border-rose-900 dark:bg-rose-950/60 text-rose-950 dark:text-rose-100 break-words">
            {event.lastFailureReason || 'Exhausted maximum retry backoff limit without successful completion.'}
          </div>
        </div>

        {/* Timestamps */}
        <div className="flex flex-wrap items-center gap-4 text-xs text-slate-500 dark:text-slate-400 pt-1">
          <div className="flex items-center gap-1.5">
            <Calendar className="h-3.5 w-3.5" />
            <span>Created: {new Date(event.createdAt).toLocaleString()}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Hash className="h-3.5 w-3.5" />
            <span>Last Attempt: {event.lastAttemptAt ? new Date(event.lastAttemptAt).toLocaleString() : 'N/A'}</span>
          </div>
        </div>

        {/* Original Request Payload */}
        <div className="space-y-1.5 pt-1">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300">
              <FileCode className="h-3.5 w-3.5" />
              Original Request Payload
            </span>
            {loadingPayload && (
              <span className="text-[11px] text-slate-400 animate-pulse">Loading payload...</span>
            )}
          </div>
          <pre className="max-h-40 overflow-auto rounded-md border border-slate-200 bg-slate-50 p-2.5 font-mono text-[11px] text-slate-800 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200">
            {formatPayload(payload) || (loadingPayload ? '// Loading...' : '// No payload recorded')}
          </pre>
        </div>
      </div>
    </Modal>
  )
}
