import type { AuthResponse } from '../types/auth'

const TOKEN_KEY = 'reliableflow_token'
const USER_KEY = 'reliableflow_user'

// In-memory fallback for SSR/Node environments where localStorage is not present
const memoryStore = new Map<string, string>()

const getStorage = () => {
  if (typeof window !== 'undefined' && window.localStorage) {
    return window.localStorage
  }
  return {
    getItem: (key: string) => memoryStore.get(key) ?? null,
    setItem: (key: string, value: string) => {
      memoryStore.set(key, value)
    },
    removeItem: (key: string) => {
      memoryStore.delete(key)
    },
  }
}

export const tokenStorage = {
  getToken: (): string | null => {
    try {
      return getStorage().getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },

  setAuth: (data: AuthResponse): void => {
    try {
      const storage = getStorage()
      storage.setItem(TOKEN_KEY, data.token)
      storage.setItem(
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
      const user = getStorage().getItem(USER_KEY)
      return user ? JSON.parse(user) : null
    } catch {
      return null
    }
  },

  clearAuth: (): void => {
    try {
      const storage = getStorage()
      storage.removeItem(TOKEN_KEY)
      storage.removeItem(USER_KEY)
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
