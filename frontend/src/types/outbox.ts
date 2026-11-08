export type OutboxStatus = 'PENDING' | 'CLAIMED' | 'PUBLISHED' | 'FAILED'

export interface OutboxDetailResponse {
  id: number
  status: OutboxStatus | string
  topic: string
  partitionKey: string
  retryCount: number
  maxRetries: number
  lastError: string | null
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface OutboxSummaryResponse {
  totalOutboxEvents: number
  pendingCount: number
  claimedCount: number
  publishedCount: number
  failedCount: number
  retriedCount: number
  countsByStatus: Record<string, number>
}

