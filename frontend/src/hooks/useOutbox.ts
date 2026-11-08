import { useState, useMemo } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { outboxService } from '../services/outboxService'
import type { OutboxSummaryResponse } from '../types/outbox'
import type { PageResponse } from '../types/api'
import type { EventDetailResponse } from '../types/events'

export const OUTBOX_KEYS = {
  all: ['outbox'] as const,
  summary: () => [...OUTBOX_KEYS.all, 'summary'] as const,
  events: (page: number, size: number, status?: string) =>
    [...OUTBOX_KEYS.all, 'events', { page, size, status }] as const,
}

export function useOutbox(initialPage = 0, initialSize = 20) {
  const [page, setPage] = useState(initialPage)
  const [size, setSize] = useState(initialSize)
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [searchKey, setSearchKey] = useState<string>('')
  const [selectedRecord, setSelectedRecord] = useState<EventDetailResponse | null>(null)

  const queryClient = useQueryClient()

  const summaryQuery = useQuery<OutboxSummaryResponse, Error>({
    queryKey: OUTBOX_KEYS.summary(),
    queryFn: () => outboxService.getOutboxSummary(),
    staleTime: 15_000,
    refetchInterval: 15_000,
  })

  const eventsQuery = useQuery<PageResponse<EventDetailResponse>, Error>({
    queryKey: OUTBOX_KEYS.events(page, size, statusFilter === 'ALL' ? undefined : statusFilter),
    queryFn: () =>
      outboxService.getOutboxEvents({
        page,
        size,
        idempotencyKey: searchKey ? searchKey.trim() : undefined,
      }),
    staleTime: 15_000,
  })

  // Filter records that have outbox representation and match status filter
  const outboxRecords = useMemo(() => {
    const content = eventsQuery.data?.content ?? []
    return content.filter((event) => {
      if (!event.outbox) return false
      if (statusFilter !== 'ALL' && event.outbox.status !== statusFilter) {
        return false
      }
      return true
    })
  }, [eventsQuery.data?.content, statusFilter])

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: OUTBOX_KEYS.all })
  }

  return {
    page,
    setPage,
    size,
    setSize,
    statusFilter,
    setStatusFilter,
    searchKey,
    setSearchKey,
    selectedRecord,
    setSelectedRecord,
    summary: summaryQuery.data,
    outboxRecords,
    rawEventsPage: eventsQuery.data,
    isLoading: summaryQuery.isLoading || eventsQuery.isLoading,
    isFetching: summaryQuery.isFetching || eventsQuery.isFetching,
    error: summaryQuery.error || eventsQuery.error,
    refreshAll,
  }
}

