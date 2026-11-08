import { RefreshCw, Eye, AlertCircle, ShieldAlert } from 'lucide-react'
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '../common/Table'
import { Badge } from '../common/Badge'
import { Button } from '../common/Button'
import { Pagination } from '../common/Pagination'
import { EmptyState } from '../common/EmptyState'
import type { DeadLetterResponse } from '../../types/events'

export interface DeadLetterTableProps {
  events: DeadLetterResponse[]
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (page: number) => void
  onInspect: (event: DeadLetterResponse) => void
  onReprocess: (idempotencyKey: string) => void
  onBulkReprocess: (keys: string[]) => void
  selectedKeys: string[]
  onToggleSelect: (key: string) => void
  onToggleSelectAll: () => void
  canReprocess: boolean
  isLoading?: boolean
}

export function DeadLetterTable({
  events,
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  onInspect,
  onReprocess,
  onBulkReprocess,
  selectedKeys,
  onToggleSelect,
  onToggleSelectAll,
  canReprocess,
  isLoading,
}: DeadLetterTableProps) {
  const allSelected = events.length > 0 && events.every((e) => selectedKeys.includes(e.idempotencyKey))
  const someSelected = events.some((e) => selectedKeys.includes(e.idempotencyKey))

  if (!isLoading && events.length === 0) {
    return (
      <EmptyState
        icon={<AlertCircle className="h-6 w-6 text-emerald-600 dark:text-emerald-400" />}
        title="Dead Letter Queue is Clean"
        description="There are currently zero exhausted events in the dead letter queue. All failures have either recovered or completed successfully."
      />
    )
  }

  return (
    <div className="space-y-3">
      {/* Bulk Action Toolbar */}
      {canReprocess && selectedKeys.length > 0 && (
        <div className="flex items-center justify-between rounded-lg border border-amber-200 bg-amber-50/80 px-4 py-2.5 text-xs text-amber-900 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-200">
          <div className="flex items-center gap-2">
            <span className="font-semibold">{selectedKeys.length}</span> event{selectedKeys.length > 1 ? 's' : ''} selected
          </div>
          <Button
            size="sm"
            onClick={() => onBulkReprocess(selectedKeys)}
            className="bg-amber-600 hover:bg-amber-700 text-white shadow-sm"
          >
            <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
            Reprocess {selectedKeys.length} Selected
          </Button>
        </div>
      )}

      {/* Main Table */}
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <Table>
          <TableHeader>
            <TableRow>
              {canReprocess && (
                <TableHead className="w-10">
                  <input
                    type="checkbox"
                    checked={allSelected}
                    ref={(el) => {
                      if (el) el.indeterminate = someSelected && !allSelected
                    }}
                    onChange={onToggleSelectAll}
                    className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 dark:border-slate-700 dark:bg-slate-800"
                    aria-label="Select all dead-letter rows"
                  />
                </TableHead>
              )}
              <TableHead>Idempotency Key</TableHead>
              <TableHead>Event Type</TableHead>
              <TableHead>Resource ID</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Retries</TableHead>
              <TableHead>Failure Reason</TableHead>
              <TableHead>Last Failed At</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {events.map((event) => {
              const isSelected = selectedKeys.includes(event.idempotencyKey)
              return (
                <TableRow
                  key={event.idempotencyKey}
                  className={isSelected ? 'bg-amber-50/40 dark:bg-amber-950/20' : undefined}
                >
                  {canReprocess && (
                    <TableCell>
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => onToggleSelect(event.idempotencyKey)}
                        className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 dark:border-slate-700 dark:bg-slate-800"
                        aria-label={`Select event ${event.idempotencyKey}`}
                      />
                    </TableCell>
                  )}

                  {/* Idempotency Key */}
                  <TableCell className="font-mono text-xs font-semibold text-slate-800 dark:text-slate-200">
                    <button
                      type="button"
                      onClick={() => onInspect(event)}
                      className="hover:text-indigo-600 dark:hover:text-indigo-400 text-left transition-colors truncate max-w-[200px] block"
                      title={event.idempotencyKey}
                    >
                      {event.idempotencyKey}
                    </button>
                  </TableCell>

                  {/* Type */}
                  <TableCell className="font-mono text-[11px] text-slate-600 dark:text-slate-300">
                    {event.eventType}
                  </TableCell>

                  {/* Resource */}
                  <TableCell className="font-mono text-[11px] text-slate-600 dark:text-slate-300">
                    {event.resourceId}
                  </TableCell>

                  {/* Status */}
                  <TableCell>
                    <Badge variant="error" dot>
                      DEAD_LETTER
                    </Badge>
                  </TableCell>

                  {/* Retries */}
                  <TableCell className="font-mono text-[11px] text-rose-600 dark:text-rose-400 font-medium">
                    {event.retryCount} / {event.maxRetries}
                  </TableCell>

                  {/* Last Failure Reason */}
                  <TableCell className="max-w-[220px]">
                    <span
                      className="font-mono text-[11px] text-slate-500 dark:text-slate-400 truncate block"
                      title={event.lastFailureReason ?? 'Exhausted maximum retry backoff'}
                    >
                      {event.lastFailureReason || 'Exhausted retries'}
                    </span>
                  </TableCell>

                  {/* Failed Date */}
                  <TableCell className="text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                    {event.updatedAt
                      ? new Date(event.updatedAt).toLocaleString([], {
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })
                      : 'N/A'}
                  </TableCell>

                  {/* Action Buttons */}
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onInspect(event)}
                        title="Inspect failure details"
                        className="h-7 px-2 text-xs"
                      >
                        <Eye className="mr-1 h-3.5 w-3.5" />
                        Inspect
                      </Button>

                      {canReprocess ? (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onReprocess(event.idempotencyKey)}
                          title="Schedule immediate reprocess"
                          className="h-7 px-2 text-xs border-amber-300 text-amber-700 hover:bg-amber-50 dark:border-amber-800 dark:text-amber-300 dark:hover:bg-amber-950/50"
                        >
                          <RefreshCw className="mr-1 h-3.5 w-3.5" />
                          Reprocess
                        </Button>
                      ) : (
                        <span
                          className="inline-flex items-center text-[10px] text-slate-400 gap-1 px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800"
                          title="Reprocessing requires OPERATOR or ADMIN role"
                        >
                          <ShieldAlert className="h-3 w-3" />
                          View Only
                        </span>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          totalElements={totalElements}
          size={pageSize}
          onPageChange={onPageChange}
        />
      )}
    </div>
  )
}
