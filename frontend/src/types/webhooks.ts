export interface WebhookResponse {
  id: number
  tenantId: string
  name: string
  destinationUrl: string
  status: string
  subscribedEvents: string
  secretPrefix?: string | null
  signingSecret?: string | null
  createdAt: string
  updatedAt?: string | null
  lastDeliveryAt?: string | null
  lastDeliveryStatus?: string | null
}

export interface CreateWebhookRequest {
  name: string
  destinationUrl: string
  subscribedEvents: string
}

export interface WebhookDeliveryResponse {
  id: number
  tenantId: string
  webhookId: number
  eventId: string
  eventType: string
  idempotencyKey?: string | null
  payload?: string | null
  status: string
  httpStatus?: number | null
  attemptCount: number
  errorMessage?: string | null
  createdAt: string
  deliveredAt?: string | null
}
