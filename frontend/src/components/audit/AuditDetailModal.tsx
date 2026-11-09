import { Calendar, User, Target, AlertTriangle, FileText } from 'lucide-react'
import { Modal } from '../common/Modal'
import { Button } from '../common/Button'
import { Badge } from '../common/Badge'
import type { OperationalAuditLogResponse } from '../../types/audit'

export interface AuditDetailModalProps {
  log: OperationalAuditLogResponse | null
  isOpen: boolean
  onClose: () => void
}

export function AuditDetailModal({ log, isOpen, onClose }: AuditDetailModalProps) {
  if (!log) return null

  const isSuccess = log.outcome === 'SUCCESS'

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Audit Record #${log.id}`}
      description="Immutable operational security and lifecycle trace"
      size="md"
      footer={
        <div className="flex w-full justify-end">
          <Button variant="outline" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Metadata Grid */}
        <div className="grid grid-cols-2 gap-3 rounded-lg border border-slate-200 bg-slate-50/50 p-3 text-xs dark:border-slate-800 dark:bg-slate-900/50">
          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Audit ID</span>
            <span className="font-mono font-medium text-slate-900 dark:text-slate-100">
              #{log.id}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Outcome</span>
            <div className="mt-0.5">
              <Badge variant={isSuccess ? 'success' : 'error'} dot>
                {log.outcome}
              </Badge>
            </div>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Action Type</span>
            <span className="font-mono font-semibold text-indigo-600 dark:text-indigo-400">
              {log.actionType}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Actor / Principal</span>
            <span className="font-medium text-slate-900 dark:text-slate-100 flex items-center gap-1">
              <User className="h-3 w-3 text-slate-400" />
              {log.principal}
            </span>
          </div>

          <div className="col-span-2">
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Target Identifier</span>
            <span className="font-mono text-slate-900 dark:text-slate-100 break-all flex items-center gap-1">
              <Target className="h-3 w-3 text-slate-400 shrink-0" />
              {log.targetIdentifier || 'N/A'}
            </span>
          </div>
        </div>

        {/* Failure Reason */}
        {log.failureReason && (
          <div className="rounded-lg border border-rose-200 bg-rose-50/70 p-3 text-xs text-rose-900 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-200">
            <div className="flex items-center gap-1.5 font-semibold">
              <AlertTriangle className="h-4 w-4 text-rose-600 dark:text-rose-400 shrink-0" />
              Failure Reason
            </div>
            <p className="mt-1 font-mono text-[11px] bg-rose-100/60 p-2 rounded dark:bg-rose-950/60 break-words">
              {log.failureReason}
            </p>
          </div>
        )}

        {/* Action Details */}
        {log.details && (
          <div className="space-y-1.5">
            <span className="flex items-center gap-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300">
              <FileText className="h-3.5 w-3.5" />
              Operational Context Details
            </span>
            <div className="rounded-md border border-slate-200 bg-slate-50 p-2.5 font-mono text-[11px] text-slate-800 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200 break-words">
              {log.details}
            </div>
          </div>
        )}

        {/* Timestamp */}
        <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400 border-t border-slate-100 pt-2 dark:border-slate-800">
          <Calendar className="h-3.5 w-3.5" />
          <span>Recorded At: {new Date(log.timestamp).toLocaleString()}</span>
        </div>
      </div>
    </Modal>
  )
}
