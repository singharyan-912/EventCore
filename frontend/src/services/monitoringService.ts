import { api, actuatorApi } from './api'
import type { ActuatorHealthResponse, OperationsMetricsResponse } from '../types/metrics'

export const monitoringService = {
  /**
   * Retrieve Spring Boot Actuator subsystem health.
   * GET /actuator/health
   */
  getHealth: async (): Promise<ActuatorHealthResponse> => {
    const { data } = await actuatorApi.get<ActuatorHealthResponse>('/health')
    return data
  },

  /**
   * Retrieve unified operational metrics: runtime in-memory metrics + durable database counts.
   * GET /api/v1/operations/metrics
   */
  getOperationsMetrics: async (): Promise<OperationsMetricsResponse> => {
    const { data } = await api.get<OperationsMetricsResponse>('/operations/metrics')
    return data
  },
}

