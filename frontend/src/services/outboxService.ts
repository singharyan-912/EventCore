import { api } from './api'
import type { PageResponse } from '../types/api'
import type { EventDetailResponse } from '../types/events'
import type { OutboxSummaryResponse } from '../types/outbox'

export const outboxService = {
  /**
   * Retrieve summary counts of outbox events by status and retry state.
   * GET /api/v1/operations/outbox/summary
   */
  getOutboxSummary: async (): Promise<OutboxSummaryResponse> => {
    const { data } = await api.get<OutboxSummaryResponse>('/operations/outbox/summary')
    return data
  },

  /**
   * Query events with joined outbox details for record monitoring.
   * GET /api/v1/operations/events
   */
  getOutboxEvents: async (params?: {
    page?: number
    size?: number
    status?: string
    eventType?: string
    idempotencyKey?: string
  }): Promise<PageResponse<EventDetailResponse>> => {
    const { data } = await api.get<PageResponse<EventDetailResponse>>('/operations/events', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 20,
        status: params?.status,
        eventType: params?.eventType,
        idempotencyKey: params?.idempotencyKey,
      },
    })
    return data
  },
}

