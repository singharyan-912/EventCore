import { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { eventService } from '../services/eventService'
import type { EventSearchParams } from '../types/events'

export interface EventFilterState {
  status: string
  eventType: string
  resourceId: string
  idempotencyKey: string
  from: string
  to: string
}

const initialFilters: EventFilterState = {
  status: '',
  eventType: '',
  resourceId: '',
  idempotencyKey: '',
  from: '',
  to: '',
}

export function useEvents(initialPage = 0, initialSize = 20) {
  const [filters, setFilters] = useState<EventFilterState>(initialFilters)
  const [appliedFilters, setAppliedFilters] = useState<EventFilterState>(initialFilters)
  const [page, setPage] = useState<number>(initialPage)
  const [size, setSize] = useState<number>(initialSize)

  const searchParams: EventSearchParams = {
    status: appliedFilters.status || undefined,
    eventType: appliedFilters.eventType || undefined,
    resourceId: appliedFilters.resourceId || undefined,
    idempotencyKey: appliedFilters.idempotencyKey || undefined,
    from: appliedFilters.from ? new Date(appliedFilters.from).toISOString() : undefined,
    to: appliedFilters.to ? new Date(appliedFilters.to).toISOString() : undefined,
    page,
    size,
  }

  const query = useQuery({
    queryKey: ['operations', 'events', searchParams],
    queryFn: () => eventService.searchEvents(searchParams),
    staleTime: 10000,
  })

  const applyFilters = useCallback(() => {
    setAppliedFilters({ ...filters })
    setPage(0) // Reset to first page when filtering
  }, [filters])

  const resetFilters = useCallback(() => {
    setFilters(initialFilters)
    setAppliedFilters(initialFilters)
    setPage(0)
  }, [])

  const handlePageChange = useCallback((newPage: number) => {
    setPage(newPage)
  }, [])

  const handleSizeChange = useCallback((newSize: number) => {
    setSize(newSize)
    setPage(0)
  }, [])

  return {
    filters,
    setFilters,
    appliedFilters,
    applyFilters,
    resetFilters,
    page,
    size,
    setPage: handlePageChange,
    setSize: handleSizeChange,
    data: query.data,
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    isError: query.isError,
    error: query.error as { message?: string } | null,
    refetch: query.refetch,
  }
}

export function useEventDetail(idempotencyKey: string | undefined) {
  return useQuery({
    queryKey: ['operations', 'event', idempotencyKey],
    queryFn: () => eventService.getEventDetail(idempotencyKey!),
    enabled: Boolean(idempotencyKey && idempotencyKey.trim().length > 0),
    staleTime: 15000,
  })
}

