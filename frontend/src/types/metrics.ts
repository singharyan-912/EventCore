import type { EventSummaryResponse } from './events'
import type { OutboxSummaryResponse } from './outbox'

export interface OperationsMetricsResponse {
  inMemoryMetricsNotice: string
  attempts: number
  successes: number
  failures: number
  duplicates: number
  retries: number
  deadLetters: number
  outboxPublished: number
  outboxFailed: number
  consumerSuccess: number
  consumerFailure: number
  consumerDuplicate: number
  databaseEvents: EventSummaryResponse
  databaseOutbox: OutboxSummaryResponse
}

export interface ActuatorHealthComponent {
  status: 'UP' | 'DOWN' | 'UNKNOWN'
  details?: Record<string, unknown>
}

export interface ActuatorHealthResponse {
  status: 'UP' | 'DOWN'
  components?: {
    db?: ActuatorHealthComponent
    kafka?: ActuatorHealthComponent
    outbox?: ActuatorHealthComponent
    retryWorker?: ActuatorHealthComponent
    diskSpace?: ActuatorHealthComponent
    ping?: ActuatorHealthComponent
  }
}
