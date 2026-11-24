import { api } from './api'
import type { ApiKeyResponse, CreateApiKeyRequest, CreateApiKeyResponse } from '../types/apiKey'

export const apiKeyService = {
  listApiKeys: async (): Promise<ApiKeyResponse[]> => {
    const { data } = await api.get<ApiKeyResponse[]>('/api-keys')
    return data
  },

  createApiKey: async (request: CreateApiKeyRequest): Promise<CreateApiKeyResponse> => {
    const { data } = await api.post<CreateApiKeyResponse>('/api-keys', request)
    return data
  },

  revokeApiKey: async (id: number): Promise<ApiKeyResponse> => {
    const { data } = await api.delete<ApiKeyResponse>(`/api-keys/${id}`)
    return data
  },

  rotateApiKey: async (id: number): Promise<CreateApiKeyResponse> => {
    const { data } = await api.post<CreateApiKeyResponse>(`/api-keys/${id}/rotate`)
    return data
  },
}
