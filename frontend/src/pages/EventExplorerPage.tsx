import { useState } from 'react'
import { RefreshCw, AlertCircle } from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { EventFilterBar } from '../components/events/EventFilterBar'
import { EventTable } from '../components/events/EventTable'
import { EventDetailModal } from '../components/events/EventDetailModal'
import { useEvents } from '../hooks/useEvents'
import type { EventDetailResponse } from '../types/events'

export function EventExplorerPage() {
  const {
    filters,
    setFilters,
    appliedFilters,
    applyFilters,
    resetFilters,
    page,
    size,
    setPage,
    setSize,
    data,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = useEvents()

  const [inspectedEvent, setInspectedEvent] = useState<EventDetailResponse | null>(null)

  const activeFilterCount = Object.values(appliedFilters).filter(Boolean).length

  return (
    <div className="space-y-6">
      {/* Page Header with Action Buttons and Count Badge */}
      <PageHeader
        title="Event Explorer"
        description="Query, filter, and inspect ingested events with correlated outbox status and idempotency hashes."
        breadcrumbs={[{ label: 'Event Explorer' }]}
        badge={
          data?.totalElements !== undefined ? (
            <Badge variant="accent">
              {data.totalElements} Total Events
            </Badge>
          ) : undefined
        }
        actions={
          <div className="flex items-center gap-2">
            {activeFilterCount > 0 && (
              <Badge variant="info">
                {activeFilterCount} Active Filter{activeFilterCount > 1 ? 's' : ''}
              </Badge>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => refetch()}
              disabled={isFetching}
            >
              <RefreshCw className={`h-3.5 w-3.5 mr-1 text-slate-500 ${isFetching ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
          </div>
        }
      />

      {/* Backend error banner if query fails */}
      {isError && (
        <div className="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-xs text-rose-900 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-200">
          <AlertCircle className="h-4 w-4 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
          <div className="space-y-1">
            <span className="font-semibold block">Event Query Failure</span>
            <p className="text-[11px] text-rose-800 dark:text-rose-300">
              {error?.message || 'Failed to query event roster from Spring Boot backend. Ensure backend is running on :8080.'}
            </p>
          </div>
        </div>
      )}

      {/* 1. Structured Filter Bar */}
      <EventFilterBar
        filters={filters}
        onFilterChange={setFilters}
        onApply={applyFilters}
        onReset={resetFilters}
        isLoading={isFetching}
      />

      {/* 2. Interactive Event Table with Pagination */}
      <EventTable
        events={data?.content || []}
        page={page}
        size={size}
        totalElements={data?.totalElements}
        totalPages={data?.totalPages}
        isLoading={isLoading}
        isFetching={isFetching}
        onPageChange={setPage}
        onSizeChange={setSize}
        onInspect={(event) => setInspectedEvent(event)}
      />

      {/* 3. Event Detail Quick Inspection Modal */}
      <EventDetailModal
        event={inspectedEvent}
        isOpen={Boolean(inspectedEvent)}
        onClose={() => setInspectedEvent(null)}
      />
    </div>
  )
}
