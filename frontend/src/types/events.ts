import type { OutboxDetailResponse } from './outbox'

export type EventStatus = 'PROCESSED' | 'RETRY_PENDING' | 'DEAD_LETTER' | 'PROCESSING'

export interface EventDetailResponse {
  idempotencyKey: string
  eventType: string
  resourceId: string
  status: EventStatus | string
  responseStatusCode: number | null
  retryCount: number
  maxRetries: number
  lastFailureReason: string | null
  nextRetryAt: string | null
  lastAttemptAt: string | null
  createdAt: string
  updatedAt: string
  requestPayload: string | null
  responsePayload: string | null
  outbox: OutboxDetailResponse | null
}

export interface EventResponse {
  idempotencyKey: string
  eventType: string
  resourceId: string
  deductedQuantity: number | null
  remainingQuantity: number | null
  status: EventStatus | string
  idempotentReplay: boolean
  processedAt: string
}

export interface EventSummaryResponse {
  totalEvents: number
  processedCount: number
  retryPendingCount: number
  deadLetterCount: number
  processingCount: number
  countsByStatus: Record<string, number>
}

export interface DeadLetterResponse {
  idempotencyKey: string
  eventType: string
  resourceId: string
  status: string
  retryCount: number
  maxRetries: number
  lastFailureReason: string | null
  nextRetryAt: string | null
  lastAttemptAt: string | null
  createdAt: string
  updatedAt: string
}

export interface EventSearchParams {
  status?: string
  eventType?: string
  resourceId?: string
  idempotencyKey?: string
  from?: string
  to?: string
  page?: number
  size?: number
}
