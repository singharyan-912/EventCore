import { Eye, Search, AlertCircle } from 'lucide-react'
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
import { Input } from '../common/Input'
import { Pagination } from '../common/Pagination'
import { EmptyState } from '../common/EmptyState'
import type { EventDetailResponse } from '../../types/events'

export interface OutboxTableProps {
  records: EventDetailResponse[]
  statusFilter: string
  onStatusChange: (status: string) => void
  searchKey: string
  onSearchChange: (val: string) => void
  onInspect: (record: EventDetailResponse) => void
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (page: number) => void
  isLoading?: boolean
}

export function OutboxTable({
  records,
  statusFilter,
  onStatusChange,
  searchKey,
  onSearchChange,
  onInspect,
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  isLoading,
}: OutboxTableProps) {
  const statusFilters = ['ALL', 'PUBLISHED', 'PENDING', 'CLAIMED', 'FAILED']

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

  return (
    <div className="space-y-3">
      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span className="text-xs font-medium text-slate-500 dark:text-slate-400 mr-1">Status:</span>
          {statusFilters.map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => onStatusChange(st)}
              className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                statusFilter === st
                  ? 'bg-indigo-600 text-white shadow-sm dark:bg-indigo-500'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700'
              }`}
            >
              {st}
            </button>
          ))}
        </div>

        <div className="w-full sm:w-64">
          <Input
            value={searchKey}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search idempotency key..."
            leftIcon={<Search className="h-4 w-4 text-slate-400" />}
          />
        </div>
      </div>

      {/* Outbox Table */}
      {!isLoading && records.length === 0 ? (
        <EmptyState
          icon={<AlertCircle className="h-6 w-6 text-slate-400" />}
          title="No Outbox Records Found"
          description={
            statusFilter === 'ALL'
              ? 'No outbox events match your search criteria. Ingest events to see ACID outbox publications here.'
              : `No outbox events currently in ${statusFilter} state.`
          }
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Outbox ID</TableHead>
                <TableHead>Idempotency Key</TableHead>
                <TableHead>Target Topic</TableHead>
                <TableHead>Partition Key</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Retries</TableHead>
                <TableHead>Queued At</TableHead>
                <TableHead>Published / Error</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {records.map((event) => {
                const outbox = event.outbox
                if (!outbox) return null

                return (
                  <TableRow key={outbox.id}>
                    {/* Outbox ID */}
                    <TableCell className="font-mono text-xs font-semibold text-slate-700 dark:text-slate-300">
                      #{outbox.id}
                    </TableCell>

                    {/* Idempotency Key */}
                    <TableCell className="font-mono text-xs font-medium text-slate-900 dark:text-slate-100">
                      <button
                        type="button"
                        onClick={() => onInspect(event)}
                        className="hover:text-indigo-600 dark:hover:text-indigo-400 text-left transition-colors truncate max-w-[180px] block"
                        title={event.idempotencyKey}
                      >
                        {event.idempotencyKey}
                      </button>
                    </TableCell>

                    {/* Topic */}
                    <TableCell className="font-mono text-[11px] text-indigo-600 dark:text-indigo-400 font-medium">
                      {outbox.topic}
                    </TableCell>

                    {/* Partition Key */}
                    <TableCell className="font-mono text-[11px] text-slate-600 dark:text-slate-300">
                      {outbox.partitionKey || '—'}
                    </TableCell>

                    {/* Status */}
                    <TableCell>{getStatusBadge(outbox.status)}</TableCell>

                    {/* Retries */}
                    <TableCell className="font-mono text-[11px] text-slate-600 dark:text-slate-300">
                      {outbox.retryCount} / {outbox.maxRetries}
                    </TableCell>

                    {/* Queued At */}
                    <TableCell className="text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                      {outbox.createdAt
                        ? new Date(outbox.createdAt).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                            second: '2-digit',
                          })
                        : '—'}
                    </TableCell>

                    {/* Published At or Error */}
                    <TableCell className="max-w-[200px]">
                      {outbox.lastError ? (
                        <span
                          className="font-mono text-[11px] text-rose-600 dark:text-rose-400 truncate block"
                          title={outbox.lastError}
                        >
                          {outbox.lastError}
                        </span>
                      ) : outbox.publishedAt ? (
                        <span className="text-xs text-emerald-600 dark:text-emerald-400 whitespace-nowrap">
                          {new Date(outbox.publishedAt).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                            second: '2-digit',
                          })}
                        </span>
                      ) : (
                        <span className="text-xs text-amber-600 dark:text-amber-400 italic">
                          Awaiting poll
                        </span>
                      )}
                    </TableCell>

                    {/* Actions */}
                    <TableCell className="text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onInspect(event)}
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
      )}

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
