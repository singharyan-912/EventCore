import { api } from './api'
import type {
  IntegrationResponse,
  CreateIntegrationRequest,
  UpdateIntegrationRequest,
  ConnectionTestResponse,
} from '../types/integrations'

export const integrationService = {
  async listIntegrations(): Promise<IntegrationResponse[]> {
    const response = await api.get<IntegrationResponse[]>('/integrations')
    return response.data
  },

  async getIntegration(id: number): Promise<IntegrationResponse> {
    const response = await api.get<IntegrationResponse>(`/integrations/${id}`)
    return response.data
  },

  async createIntegration(req: CreateIntegrationRequest): Promise<IntegrationResponse> {
    const response = await api.post<IntegrationResponse>('/integrations', req)
    return response.data
  },

  async updateIntegration(id: number, req: UpdateIntegrationRequest): Promise<IntegrationResponse> {
    const response = await api.put<IntegrationResponse>(`/integrations/${id}`, req)
    return response.data
  },

  async deleteIntegration(id: number): Promise<void> {
    await api.delete(`/integrations/${id}`)
  },

  async testConnection(id: number): Promise<ConnectionTestResponse> {
    const response = await api.post<ConnectionTestResponse>(`/integrations/${id}/test`)
    return response.data
  },
}
