import type { AuthResponse } from '../types/auth'

const TOKEN_KEY = 'reliableflow_token'
const USER_KEY = 'reliableflow_user'

export const tokenStorage = {
  getToken: (): string | null => {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },

  setAuth: (data: AuthResponse): void => {
    try {
      localStorage.setItem(TOKEN_KEY, data.token)
      localStorage.setItem(
        USER_KEY,
        JSON.stringify({
          username: data.username,
          role: data.role,
          expiresAt: Date.now() + data.expiresInMs,
        })
      )
    } catch (e) {
      console.error('Failed to save auth to storage', e)
    }
  },

  getUser: (): { username: string; role: string; expiresAt: number } | null => {
    try {
      const user = localStorage.getItem(USER_KEY)
      return user ? JSON.parse(user) : null
    } catch {
      return null
    }
  },

  clearAuth: (): void => {
    try {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    } catch (e) {
      console.error('Failed to clear auth from storage', e)
    }
  },

  isAuthenticated: (): boolean => {
    const token = tokenStorage.getToken()
    const user = tokenStorage.getUser()
    if (!token || !user) return false
    return Date.now() < user.expiresAt
  },
}
