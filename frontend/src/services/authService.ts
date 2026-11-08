import { api } from './api'
import type { AuthResponse, LoginRequest, UserProfileResponse } from '../types/auth'
import { tokenStorage } from '../utils/tokenStorage'

export const authService = {
  login: async (credentials: LoginRequest): Promise<AuthResponse> => {
    const { data } = await api.post<AuthResponse>('/auth/login', credentials)
    tokenStorage.setAuth(data)
    return data
  },

  getProfile: async (): Promise<UserProfileResponse> => {
    const { data } = await api.get<UserProfileResponse>('/auth/me')
    return data
  },

  logout: async (): Promise<void> => {
    try {
      await api.post('/auth/logout')
    } finally {
      tokenStorage.clearAuth()
    }
  },
}
