export interface OperationalAuditLogResponse {
  id: number
  actionType: string
  targetIdentifier: string
  outcome: 'SUCCESS' | 'FAILED' | string
  failureReason: string | null
  principal: string
  details: string | null
  timestamp: string
}

