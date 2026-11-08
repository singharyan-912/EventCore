import { useState, useEffect, useMemo } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { monitoringService } from '../services/monitoringService'
import type { ActuatorHealthResponse, OperationsMetricsResponse } from '../types/metrics'

export const MONITORING_KEYS = {
  all: ['monitoring'] as const,
  health: () => [...MONITORING_KEYS.all, 'health'] as const,
  metrics: () => [...MONITORING_KEYS.all, 'metrics'] as const,
}

export interface MetricSnapshot {
  time: string
  attempts: number
  successes: number
  failures: number
  outboxPublished: number
}

export function useMonitoring(pollIntervalMs = 15_000) {
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [snapshots, setSnapshots] = useState<MetricSnapshot[]>([])

  const queryClient = useQueryClient()

  const healthQuery = useQuery<ActuatorHealthResponse, Error>({
    queryKey: MONITORING_KEYS.health(),
    queryFn: monitoringService.getHealth,
    refetchInterval: autoRefresh ? pollIntervalMs : false,
    staleTime: 10_000,
  })

  const metricsQuery = useQuery<OperationsMetricsResponse, Error>({
    queryKey: MONITORING_KEYS.metrics(),
    queryFn: monitoringService.getOperationsMetrics,
    refetchInterval: autoRefresh ? pollIntervalMs : false,
    staleTime: 10_000,
  })

  const lastRefreshed = useMemo(() => {
    return metricsQuery.dataUpdatedAt ? new Date(metricsQuery.dataUpdatedAt) : new Date(0)
  }, [metricsQuery.dataUpdatedAt])

  // Append session snapshot when metrics data updates (keep at most 12 points)
  useEffect(() => {
    if (metricsQuery.data) {
      const nowStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      const newPoint: MetricSnapshot = {
        time: nowStr,
        attempts: metricsQuery.data.attempts,
        successes: metricsQuery.data.successes,
        failures: metricsQuery.data.failures,
        outboxPublished: metricsQuery.data.outboxPublished,
      }

      queueMicrotask(() => {
        setSnapshots((prev) => {
          // Prevent immediate duplicate if identical timestamp and values
          const last = prev[prev.length - 1]
          if (last && last.time === newPoint.time && last.attempts === newPoint.attempts) {
            return prev
          }
          const updated = [...prev, newPoint]
          return updated.slice(-12)
        })
      })
    }
  }, [metricsQuery.data])

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: MONITORING_KEYS.all })
  }

  return {
    health: healthQuery.data,
    metrics: metricsQuery.data,
    isLoading: healthQuery.isLoading || metricsQuery.isLoading,
    isFetching: healthQuery.isFetching || metricsQuery.isFetching,
    error: healthQuery.error || metricsQuery.error,
    autoRefresh,
    setAutoRefresh,
    snapshots,
    lastRefreshed,
    refreshAll,
  }
}
