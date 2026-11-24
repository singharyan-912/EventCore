export type ApiKeyStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

export interface ApiKeyResponse {
  id: number
  name: string
  keyPrefix: string
  status: ApiKeyStatus
  createdAt: string
  lastUsedAt: string | null
  expiresAt: string | null
  revokedAt: string | null
  createdBy: string
}

export interface CreateApiKeyRequest {
  name: string
  expiresAt?: string | null
}

export interface CreateApiKeyResponse {
  id: number
  name: string
  keyPrefix: string
  secretKey: string
  status: ApiKeyStatus
  createdAt: string
  expiresAt: string | null
  message: string
}
