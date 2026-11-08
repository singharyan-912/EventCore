import axios from 'axios'
import { tokenStorage } from '../utils/tokenStorage'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request Interceptor: Attach JWT Bearer Token
api.interceptors.request.use(
  (config) => {
    const token = tokenStorage.getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response Interceptor: Handle 401 Unauthorized
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      tokenStorage.clearAuth()
      // Only redirect if not already on the login page
      if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
        window.location.href = '/login?expired=true'
      }
    }
    return Promise.reject(error)
  }
)

