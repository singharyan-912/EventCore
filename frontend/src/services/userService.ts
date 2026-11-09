import { api } from './api'
import type { PageResponse } from '../types/api'
import type { UserResponse, CreateUserRequest } from '../types/user'
import type { Role, UserStatus } from '../types/auth'

export const userService = {
  /**
   * List all system users with pagination.
   * GET /api/v1/admin/users?page=0&size=20
   * Requires ADMIN role.
   */
  listUsers: async (page = 0, size = 20): Promise<PageResponse<UserResponse>> => {
    const { data } = await api.get<PageResponse<UserResponse>>('/admin/users', {
      params: { page, size },
    })
    return data
  },

  /**
   * Create a new console user.
   * POST /api/v1/admin/users
   * Requires ADMIN role.
   */
  createUser: async (request: CreateUserRequest): Promise<UserResponse> => {
    const { data } = await api.post<UserResponse>('/admin/users', request)
    return data
  },

  /**
   * Update an existing user's RBAC role.
   * PATCH /api/v1/admin/users/{id}/role
   * Requires ADMIN role. Protected by last active admin lockout check.
   */
  updateUserRole: async (id: number, role: Role): Promise<UserResponse> => {
    const { data } = await api.patch<UserResponse>(`/admin/users/${id}/role`, { role })
    return data
  },

  /**
   * Update an existing user's status (ACTIVE / DEACTIVATED).
   * PATCH /api/v1/admin/users/{id}/status
   * Requires ADMIN role. Protected by last active admin lockout check.
   */
  updateUserStatus: async (id: number, status: UserStatus): Promise<UserResponse> => {
    const { data } = await api.patch<UserResponse>(`/admin/users/${id}/status`, { status })
    return data
  },
}
