import { api } from './api'
import type { PageResponse } from '../types/api'
import type {
  ReconciliationDetailResponse,
  ReconcileRequest,
  ReconcileResponse,
} from '../types/reconciliation'

export const reconciliationService = {
  async listReconciliations(page = 0, size = 20): Promise<PageResponse<ReconciliationDetailResponse>> {
    const response = await api.get<PageResponse<ReconciliationDetailResponse>>('/operations/reconciliations', {
      params: { page, size },
    })
    return response.data
  },

  async getReconciliation(idempotencyKey: string): Promise<ReconciliationDetailResponse> {
    const response = await api.get<ReconciliationDetailResponse>(`/operations/reconciliations/${idempotencyKey}`)
    return response.data
  },

  async verifyAutomatically(idempotencyKey: string): Promise<ReconcileResponse> {
    const response = await api.post<ReconcileResponse>(`/operations/reconciliations/${idempotencyKey}/verify`)
    return response.data
  },

  async resolveManually(idempotencyKey: string, req: ReconcileRequest): Promise<ReconcileResponse> {
    const response = await api.post<ReconcileResponse>(`/operations/reconciliations/${idempotencyKey}/resolve`, req)
    return response.data
  },
}
