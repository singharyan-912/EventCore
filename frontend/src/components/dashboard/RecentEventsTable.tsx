import { Link, useNavigate } from 'react-router-dom'
import { ArrowUpRight, Clock, Layers } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../common/Card'
import { Badge } from '../common/Badge'
import { Button } from '../common/Button'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '../common/Table'
import type { EventDetailResponse, EventStatus } from '../../types/events'

interface RecentEventsTableProps {
  events: EventDetailResponse[]
  isLoading?: boolean
}

export function RecentEventsTable({ events, isLoading }: RecentEventsTableProps) {
  const navigate = useNavigate()

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
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    } catch {
      return dateString
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle>Recent Ingested Events</CardTitle>
            <CardDescription>
              Latest event arrivals processed through PostgreSQL and transactional outbox.
            </CardDescription>
          </div>
          <Link to="/events">
            <Button variant="outline" size="sm">
              Open Event Explorer
              <ArrowUpRight className="h-3.5 w-3.5 ml-1" />
            </Button>
          </Link>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="py-8 text-center text-xs text-slate-400">
            Loading recent events...
          </div>
        ) : events.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 text-center">
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500 mb-2">
              <Layers className="h-5 w-5" />
            </div>
            <p className="text-xs font-semibold text-slate-700 dark:text-slate-300">No events found</p>
            <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
              Ingested events from POST /api/v1/events will appear here in real-time.
            </p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Idempotency Key</TableHead>
                <TableHead>Event Type</TableHead>
                <TableHead>Resource ID</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created At</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {events.map((event) => (
                <TableRow key={event.idempotencyKey}>
                  <TableCell className="font-mono font-medium text-indigo-600 dark:text-indigo-400 max-w-[180px] truncate">
                    <span title={event.idempotencyKey}>{event.idempotencyKey}</span>
                  </TableCell>
                  <TableCell className="font-medium text-slate-800 dark:text-slate-200">
                    {event.eventType}
                  </TableCell>
                  <TableCell className="font-mono text-slate-600 dark:text-slate-300">
                    {event.resourceId}
                  </TableCell>
                  <TableCell>{getStatusBadge(event.status)}</TableCell>
                  <TableCell className="text-slate-500 dark:text-slate-400">
                    <div className="flex items-center gap-1">
                      <Clock className="h-3 w-3 text-slate-400" />
                      <span>{formatTimestamp(event.createdAt)}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => navigate(`/events/${encodeURIComponent(event.idempotencyKey)}`)}
                      className="h-7 text-xs"
                    >
                      Inspect
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

