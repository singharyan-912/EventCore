export interface ReconciliationDetailResponse {
  idempotencyKey: string
  tenantId: string
  eventType: string
  resourceId: string
  status: string
  lastFailureReason?: string | null
  lastAttemptAt?: string | null
  reconciledAt?: string | null
  reconciledBy?: string | null
  reconciliationNotes?: string | null
  requestPayload?: string | null
}

export interface ReconcileRequest {
  resolution: 'SUCCESS' | 'FAILURE'
  reason: string
  notes?: string
}

export interface ReconcileResponse {
  idempotencyKey: string
  tenantId: string
  status: string
  reconciledAt?: string | null
  reconciledBy?: string | null
  reconciliationNotes?: string | null
  message: string
}
