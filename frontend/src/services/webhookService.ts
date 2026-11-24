import { api } from './api'
import type {
  WebhookResponse,
  CreateWebhookRequest,
  WebhookDeliveryResponse,
} from '../types/webhooks'

export const webhookService = {
  async listWebhooks(): Promise<WebhookResponse[]> {
    const response = await api.get<WebhookResponse[]>('/webhooks')
    return response.data
  },

  async getWebhook(id: number): Promise<WebhookResponse> {
    const response = await api.get<WebhookResponse>(`/webhooks/${id}`)
    return response.data
  },

  async createWebhook(req: CreateWebhookRequest): Promise<WebhookResponse> {
    const response = await api.post<WebhookResponse>('/webhooks', req)
    return response.data
  },

  async rotateSecret(id: number): Promise<WebhookResponse> {
    const response = await api.post<WebhookResponse>(`/webhooks/${id}/rotate-secret`)
    return response.data
  },

  async deleteWebhook(id: number): Promise<void> {
    await api.delete(`/webhooks/${id}`)
  },

  async listDeliveries(webhookId: number): Promise<WebhookDeliveryResponse[]> {
    const response = await api.get<WebhookDeliveryResponse[]>(`/webhooks/${webhookId}/deliveries`)
    return response.data
  },

  async redeliver(webhookId: number, deliveryId: number): Promise<WebhookDeliveryResponse> {
    const response = await api.post<WebhookDeliveryResponse>(`/webhooks/${webhookId}/deliveries/${deliveryId}/redeliver`)
    return response.data
  },
}
