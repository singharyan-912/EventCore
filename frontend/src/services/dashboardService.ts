import { api, actuatorApi } from './api'
import type { OperationsMetricsResponse, ActuatorHealthResponse } from '../types/metrics'
import type { EventSummaryResponse } from '../types/events'
import type { OutboxSummaryResponse } from '../types/outbox'

export const dashboardService = {
  /**
   * Fetches unified operational metrics: process in-memory counters + database totals
   * Endpoint: GET /api/v1/operations/metrics
   */
  getOperationsMetrics: async (): Promise<OperationsMetricsResponse> => {
    const { data } = await api.get<OperationsMetricsResponse>('/operations/metrics')
    return data
  },

  /**
   * Fetches persistent event summary counts directly grouped by status
   * Endpoint: GET /api/v1/operations/events/summary
   */
  getEventSummary: async (): Promise<EventSummaryResponse> => {
    const { data } = await api.get<EventSummaryResponse>('/operations/events/summary')
    return data
  },

  /**
   * Fetches persistent outbox publication summary counts
   * Endpoint: GET /api/v1/operations/outbox/summary
   */
  getOutboxSummary: async (): Promise<OutboxSummaryResponse> => {
    const { data } = await api.get<OutboxSummaryResponse>('/operations/outbox/summary')
    return data
  },

  /**
   * Queries Spring Boot Actuator health endpoint
   * Endpoint: GET /actuator/health
   */
  getHealth: async (): Promise<ActuatorHealthResponse> => {
    const { data } = await actuatorApi.get<ActuatorHealthResponse>('/health')
    return data
  },
}

