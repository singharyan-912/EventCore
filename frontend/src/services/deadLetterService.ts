import { api } from './api'
import type { PageResponse } from '../types/api'
import type { DeadLetterResponse } from '../types/events'

export const deadLetterService = {
  /**
   * List all dead-letter events with pagination.
   * GET /api/v1/admin/dead-letters?page=0&size=20
   * Requires ADMIN or OPERATOR role.
   */
  listDeadLetters: async (page = 0, size = 20): Promise<PageResponse<DeadLetterResponse>> => {
    const { data } = await api.get<PageResponse<DeadLetterResponse>>('/admin/dead-letters', {
      params: { page, size },
    })
    return data
  },

  /**
   * Retrieve details of a specific dead-letter event.
   * GET /api/v1/admin/dead-letters/{idempotencyKey}
   */
  getDeadLetter: async (idempotencyKey: string): Promise<DeadLetterResponse> => {
    const { data } = await api.get<DeadLetterResponse>(
      `/admin/dead-letters/${encodeURIComponent(idempotencyKey)}`
    )
    return data
  },

  /**
   * Request operator-initiated reprocessing of a dead-letter event.
   * POST /api/v1/admin/dead-letters/{idempotencyKey}/reprocess
   * Schedules event for immediate retry by RetryWorker.
   */
  reprocessDeadLetter: async (idempotencyKey: string): Promise<DeadLetterResponse> => {
    const { data } = await api.post<DeadLetterResponse>(
      `/admin/dead-letters/${encodeURIComponent(idempotencyKey)}/reprocess`
    )
    return data
  },
}

