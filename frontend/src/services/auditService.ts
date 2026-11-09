import { api } from './api'
import type { PageResponse } from '../types/api'
import type { OperationalAuditLogResponse } from '../types/audit'

export interface AuditLogSearchParams {
  actionType?: string
  targetIdentifier?: string
  outcome?: string
  page?: number
  size?: number
}

export const auditService = {
  /**
   * Query operational audit logs with pagination and optional filtering.
   * GET /api/v1/operations/audit-logs
   * Requires ADMIN role.
   */
  getAuditLogs: async (params?: AuditLogSearchParams): Promise<PageResponse<OperationalAuditLogResponse>> => {
    const { data } = await api.get<PageResponse<OperationalAuditLogResponse>>('/operations/audit-logs', {
      params: {
        page: params?.page ?? 0,
        size: params?.size ?? 20,
        actionType: params?.actionType ? params.actionType.trim() : undefined,
        targetIdentifier: params?.targetIdentifier ? params.targetIdentifier.trim() : undefined,
        outcome: params?.outcome && params.outcome !== 'ALL' ? params.outcome.trim() : undefined,
      },
    })
    return data
  },
}
