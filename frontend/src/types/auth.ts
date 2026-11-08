export type Role = 'ADMIN' | 'OPERATOR' | 'VIEWER'

export type UserStatus = 'ACTIVE' | 'DEACTIVATED'

export interface AuthResponse {
  token: string
  tokenType: string
  expiresInMs: number
  username: string
  role: Role
}

export interface UserProfileResponse {
  id: number
  username: string
  email: string
  role: Role
  status: UserStatus
}

export interface LoginRequest {
  username: string
  password: string
}

