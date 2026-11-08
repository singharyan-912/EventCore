import { api } from './api'
import type { EventDetailResponse, EventSearchParams } from '../types/events'
import type { PageResponse } from '../types/api'

export const eventService = {
  /**
   * Search and filter events with pagination.
   * Endpoint: GET /api/v1/operations/events
   */
  searchEvents: async (params: EventSearchParams = {}): Promise<PageResponse<EventDetailResponse>> => {
    // Clean empty query parameters
    const queryParams: Record<string, string | number> = {}
    if (params.status && params.status !== 'ALL') queryParams.status = params.status
    if (params.eventType?.trim()) queryParams.eventType = params.eventType.trim()
    if (params.resourceId?.trim()) queryParams.resourceId = params.resourceId.trim()
    if (params.idempotencyKey?.trim()) queryParams.idempotencyKey = params.idempotencyKey.trim()
    if (params.from) queryParams.from = params.from
    if (params.to) queryParams.to = params.to
    queryParams.page = params.page ?? 0
    queryParams.size = params.size ?? 20

    const { data } = await api.get<PageResponse<EventDetailResponse>>('/operations/events', {
      params: queryParams,
    })
    return data
  },

  /**
   * Fetch single event detailed record including correlated outbox publication.
   * Endpoint: GET /api/v1/operations/events/{idempotencyKey}
   */
  getEventDetail: async (idempotencyKey: string): Promise<EventDetailResponse> => {
    const trimmed = idempotencyKey.trim()
    const { data } = await api.get<EventDetailResponse>(`/operations/events/${encodeURIComponent(trimmed)}`)
    return data
  },
}

