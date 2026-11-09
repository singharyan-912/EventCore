import { Eye, ShieldAlert } from 'lucide-react'
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
import type { OperationalAuditLogResponse } from '../../types/audit'

export interface AuditTableProps {
  logs: OperationalAuditLogResponse[]
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (newPage: number) => void
  onInspect: (log: OperationalAuditLogResponse) => void
  isLoading?: boolean
}

export function AuditTable({
  logs,
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  onInspect,
  isLoading,
}: AuditTableProps) {
  if (!isLoading && logs.length === 0) {
    return (
      <EmptyState
        icon={<ShieldAlert className="h-6 w-6 text-slate-400" />}
        title="No Audit Records Found"
        description="No operational audit records match the selected filter criteria."
      />
    )
  }

  return (
    <div className="space-y-3">
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-xs dark:border-slate-800 dark:bg-slate-900">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Audit ID</TableHead>
              <TableHead>Action Type</TableHead>
              <TableHead>Target Identifier</TableHead>
              <TableHead>Outcome</TableHead>
              <TableHead>Actor / Principal</TableHead>
              <TableHead>Recorded Timestamp</TableHead>
              <TableHead>Context Summary</TableHead>
              <TableHead className="text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {logs.map((log) => {
              const isSuccess = log.outcome === 'SUCCESS'
              return (
                <TableRow key={log.id}>
                  {/* ID */}
                  <TableCell className="font-mono text-xs font-semibold text-slate-700 dark:text-slate-300">
                    #{log.id}
                  </TableCell>

                  {/* Action Type */}
                  <TableCell className="font-mono text-xs font-medium text-indigo-600 dark:text-indigo-400">
                    {log.actionType}
                  </TableCell>

                  {/* Target */}
                  <TableCell className="font-mono text-[11px] text-slate-700 dark:text-slate-300 max-w-[160px] truncate">
                    {log.targetIdentifier || '—'}
                  </TableCell>

                  {/* Outcome */}
                  <TableCell>
                    <Badge variant={isSuccess ? 'success' : 'error'} dot>
                      {log.outcome}
                    </Badge>
                  </TableCell>

                  {/* Principal */}
                  <TableCell className="text-xs font-medium text-slate-800 dark:text-slate-200">
                    {log.principal}
                  </TableCell>

                  {/* Timestamp */}
                  <TableCell className="text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                    {new Date(log.timestamp).toLocaleString([], {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </TableCell>

                  {/* Context / Details */}
                  <TableCell className="max-w-[200px]">
                    <span
                      className="text-xs text-slate-500 dark:text-slate-400 truncate block font-mono text-[11px]"
                      title={log.failureReason || log.details || ''}
                    >
                      {log.failureReason ? `Fail: ${log.failureReason}` : log.details || '—'}
                    </span>
                  </TableCell>

                  {/* Actions */}
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onInspect(log)}
                      className="h-7 px-2 text-xs"
                    >
                      <Eye className="mr-1 h-3.5 w-3.5" />
                      Inspect
                    </Button>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

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
