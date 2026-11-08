import type { Role, UserStatus } from './auth'

export interface UserResponse {
  id: number
  username: string
  email: string
  role: Role
  status: UserStatus
  createdAt: string
  updatedAt: string
}

export interface CreateUserRequest {
  username: string
  email: string
  password: string
  role: Role
}

export interface UpdateRoleRequest {
  role: Role
}

export interface UpdateStatusRequest {
  status: UserStatus
}
