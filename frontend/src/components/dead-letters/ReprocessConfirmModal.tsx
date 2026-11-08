import { AlertTriangle, CheckCircle, RefreshCw } from 'lucide-react'
import { Modal } from '../common/Modal'
import { Button } from '../common/Button'
import { Spinner } from '../common/Spinner'

export interface ReprocessConfirmModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirm: () => void
  idempotencyKeys: string[]
  isProcessing: boolean
  currentProgress?: { current: number; total: number }
  resultMessage?: { type: 'success' | 'error'; message: string } | null
}

export function ReprocessConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  idempotencyKeys,
  isProcessing,
  currentProgress,
  resultMessage,
}: ReprocessConfirmModalProps) {
  const isMultiple = idempotencyKeys.length > 1

  return (
    <Modal
      isOpen={isOpen}
      onClose={isProcessing ? () => {} : onClose}
      title={isMultiple ? `Reprocess ${idempotencyKeys.length} Dead Letters` : 'Reprocess Dead Letter Event'}
      size="md"
      footer={
        <div className="flex w-full items-center justify-between">
          <Button
            variant="outline"
            size="sm"
            onClick={onClose}
            disabled={isProcessing}
          >
            {resultMessage ? 'Close' : 'Cancel'}
          </Button>

          {!resultMessage && (
            <Button
              variant="primary"
              size="sm"
              onClick={onConfirm}
              disabled={isProcessing}
              className="bg-amber-600 hover:bg-amber-700 text-white"
            >
              {isProcessing ? (
                <>
                  <Spinner size="sm" className="mr-2 text-white" />
                  {currentProgress
                    ? `Processing ${currentProgress.current} of ${currentProgress.total}...`
                    : 'Re-queueing...'}
                </>
              ) : (
                <>
                  <RefreshCw className="mr-1.5 h-4 w-4" />
                  Confirm & Reprocess
                </>
              )}
            </Button>
          )}
        </div>
      }
    >
      <div className="space-y-4">
        {resultMessage ? (
          <div
            className={`flex items-start gap-3 rounded-lg border p-3.5 text-xs ${
              resultMessage.type === 'success'
                ? 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300'
                : 'border-rose-200 bg-rose-50 text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300'
            }`}
          >
            {resultMessage.type === 'success' ? (
              <CheckCircle className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
            ) : (
              <AlertTriangle className="h-4 w-4 shrink-0 text-rose-600 dark:text-rose-400" />
            )}
            <div>
              <p className="font-semibold">{resultMessage.type === 'success' ? 'Success' : 'Reprocessing Failed'}</p>
              <p className="mt-0.5">{resultMessage.message}</p>
            </div>
          </div>
        ) : (
          <>
            <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50/60 p-3 text-xs text-amber-900 dark:border-amber-900/40 dark:bg-amber-950/30 dark:text-amber-200">
              <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
              <div>
                <p className="font-semibold">Operator Reprocessing Action</p>
                <p className="mt-1 leading-relaxed text-amber-800/90 dark:text-amber-300/90">
                  This schedules the dead-letter event for immediate retry by resetting its status to{' '}
                  <code className="font-mono font-bold">RETRY_PENDING</code>. The RetryWorker will pick it up on the next polling cycle.
                </p>
                <p className="mt-1.5 text-[11px] text-amber-700/80 dark:text-amber-400/80">
                  Reliability Guarantee: Idempotency is preserved. Any business actions (e.g., inventory deduction) that already succeeded will not be duplicated.
                </p>
              </div>
            </div>

            <div>
              <span className="text-xs font-semibold text-slate-700 dark:text-slate-300 block mb-1">
                Target Event{isMultiple ? 's' : ''}:
              </span>
              <div className="max-h-32 overflow-y-auto rounded border border-slate-200 bg-slate-50 p-2 font-mono text-[11px] text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 space-y-1">
                {idempotencyKeys.map((key) => (
                  <div key={key} className="truncate">
                    • {key}
                  </div>
                ))}
              </div>
            </div>

            {isMultiple && (
              <p className="text-[11px] text-slate-500 dark:text-slate-400 italic">
                Note: Events will be reprocessed sequentially via the verified single-event endpoint.
              </p>
            )}
          </>
        )}
      </div>
    </Modal>
  )
}

