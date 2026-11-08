import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, useCallback } from 'react'
import { dashboardService } from '../services/dashboardService'
import { eventService } from '../services/eventService'

export function useDashboard() {
  const queryClient = useQueryClient()
  const [lastRefreshed, setLastRefreshed] = useState<Date>(() => new Date())

  // 1. Unified operational metrics query (in-memory + database)
  const metricsQuery = useQuery({
    queryKey: ['operations', 'metrics'],
    queryFn: dashboardService.getOperationsMetrics,
    refetchInterval: 20000, // moderate 20s polling interval
    staleTime: 10000,
  })

  // 2. Actuator health query
  const healthQuery = useQuery({
    queryKey: ['actuator', 'health'],
    queryFn: dashboardService.getHealth,
    refetchInterval: 20000,
    staleTime: 10000,
    retry: 1,
  })

  // 3. Recent 5 events query for the overview table
  const recentEventsQuery = useQuery({
    queryKey: ['operations', 'events', 'recent'],
    queryFn: () => eventService.searchEvents({ page: 0, size: 5 }),
    refetchInterval: 20000,
    staleTime: 10000,
  })

  const refreshAll = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['operations', 'metrics'] }),
      queryClient.invalidateQueries({ queryKey: ['actuator', 'health'] }),
      queryClient.invalidateQueries({ queryKey: ['operations', 'events', 'recent'] }),
    ])
    setLastRefreshed(new Date())
  }, [queryClient])

  const isLoading = metricsQuery.isLoading && healthQuery.isLoading
  const isFetching = metricsQuery.isFetching || healthQuery.isFetching || recentEventsQuery.isFetching
  const isError = metricsQuery.isError || healthQuery.isError
  const errorMessage =
    (metricsQuery.error as { message?: string })?.message ||
    (healthQuery.error as { message?: string })?.message ||
    null

  return {
    metrics: metricsQuery.data,
    health: healthQuery.data,
    recentEvents: recentEventsQuery.data?.content || [],
    isLoading,
    isFetching,
    isError,
    errorMessage,
    lastRefreshed,
    refreshAll,
  }
}
