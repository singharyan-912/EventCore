export interface IntegrationResponse {
  id: number
  tenantId: string
  name: string
  integrationType: string
  baseUrl: string
  status: string
  supportedOperationTypes: string
  authType: string
  authHeaderName?: string | null
  secretPrefix?: string | null
  timeoutMs: number
  createdAt: string
  updatedAt?: string | null
  lastConnectionAt?: string | null
  lastConnectionStatus?: string | null
  lastConnectionError?: string | null
}

export interface CreateIntegrationRequest {
  name: string
  baseUrl: string
  supportedOperationTypes: string
  authType?: string
  authHeaderName?: string
  secret?: string
  timeoutMs?: number
  integrationType?: string
}

export interface UpdateIntegrationRequest {
  name?: string
  baseUrl?: string
  supportedOperationTypes?: string
  authType?: string
  authHeaderName?: string
  secret?: string
  timeoutMs?: number
  status?: string
}

export interface ConnectionTestResponse {
  integrationId: number
  successful: boolean
  message: string
  testedAt: string
}
