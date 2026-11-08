import { Clock, Eye, Layers } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../common/Card'
import { Badge } from '../common/Badge'
import { Button } from '../common/Button'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../common/Table'
import { Pagination } from '../common/Pagination'
import { EmptyState } from '../common/EmptyState'
import type { EventDetailResponse, EventStatus } from '../../types/events'

interface EventTableProps {
  events: EventDetailResponse[]
  page: number
  size: number
  totalElements?: number
  totalPages?: number
  isLoading?: boolean
  isFetching?: boolean
  onPageChange: (newPage: number) => void
  onSizeChange: (newSize: number) => void
  onInspect: (event: EventDetailResponse) => void
}

export function EventTable({
  events,
  page,
  size,
  totalElements = 0,
  totalPages = 0,
  isLoading,
  isFetching,
  onPageChange,
  onSizeChange,
  onInspect,
}: EventTableProps) {
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

  const formatTimestamp = (dateString?: string) => {
    if (!dateString) return '—'
    try {
      const d = new Date(dateString)
      return d.toLocaleString([], {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    } catch {
      return dateString
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle>Ingested Events Directory</CardTitle>
            <CardDescription>
              Authoritative records from PostgreSQL processed_events with correlated outbox status.
            </CardDescription>
          </div>
          {isFetching && (
            <span className="text-[11px] font-mono text-indigo-600 dark:text-indigo-400 animate-pulse">
              Syncing with backend...
            </span>
          )}
        </div>
      </CardHeader>
      <CardContent className="p-0">
        {isLoading ? (
          <div className="py-16 text-center text-xs text-slate-400">
            Querying event records from PostgreSQL...
          </div>
        ) : events.length === 0 ? (
          <div className="p-6">
            <EmptyState
              icon={<Layers className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />}
              title="No Matching Events Found"
              description="No events match your current filter parameters or the database is currently empty. Try clearing the filters or submitting an event via POST /api/v1/events."
            />
          </div>
        ) : (
          <div className="divide-y divide-slate-100 dark:divide-slate-800">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Idempotency Key</TableHead>
                  <TableHead>Event Type</TableHead>
                  <TableHead>Resource ID</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Retries</TableHead>
                  <TableHead>Created At</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.map((event) => (
                  <TableRow key={event.idempotencyKey}>
                    {/* Key */}
                    <TableCell className="font-mono font-medium text-indigo-600 dark:text-indigo-400 max-w-[190px] truncate">
                      <span title={event.idempotencyKey}>{event.idempotencyKey}</span>
                    </TableCell>

                    {/* Type */}
                    <TableCell className="font-semibold text-slate-800 dark:text-slate-200">
                      {event.eventType}
                    </TableCell>

                    {/* Resource ID */}
                    <TableCell className="font-mono text-slate-600 dark:text-slate-300">
                      {event.resourceId}
                    </TableCell>

                    {/* Status Badge */}
                    <TableCell>{getStatusBadge(event.status)}</TableCell>

                    {/* Retry Info */}
                    <TableCell className="font-mono text-[11px] text-slate-500 dark:text-slate-400">
                      {event.retryCount} / {event.maxRetries}
                    </TableCell>

                    {/* Timestamp */}
                    <TableCell className="text-slate-500 dark:text-slate-400 text-[11px]">
                      <div className="flex items-center gap-1.5">
                        <Clock className="h-3 w-3 text-slate-400 shrink-0" />
                        <span>{formatTimestamp(event.createdAt)}</span>
                      </div>
                    </TableCell>

                    {/* Action buttons */}
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => onInspect(event)}
                          className="h-7 text-xs"
                          title="Quick Inspect Modal"
                        >
                          <Eye className="h-3 w-3 mr-1" />
                          Inspect
                        </Button>
                        <Link to={`/events/${encodeURIComponent(event.idempotencyKey)}`}>
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs"
                            title="Open Full Detail Page"
                          >
                            Details
                          </Button>
                        </Link>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {/* Pagination Controls */}
            <div className="p-3">
              <Pagination
                page={page}
                totalPages={totalPages}
                totalElements={totalElements}
                size={size}
                onPageChange={onPageChange}
                onSizeChange={onSizeChange}
              />
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

