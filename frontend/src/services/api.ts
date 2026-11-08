import axios, { type AxiosError } from 'axios'
import { tokenStorage } from '../utils/tokenStorage'

export interface ApiErrorDetail {
  status?: number
  message: string
  details?: string[]
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Actuator client targeting the /actuator proxy directly
export const actuatorApi = axios.create({
  baseURL: '/actuator',
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request Interceptor: Attach JWT Bearer Token if present
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

// Response Interceptor: Centralize error handling for 401, 403, network errors
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ message?: string; error?: string; details?: string[] }>) => {
    const status = error.response?.status
    const serverMessage = error.response?.data?.message || error.response?.data?.error

    if (status === 401) {
      // Clear expired or invalid credentials
      tokenStorage.clearAuth()
      if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
        window.location.href = '/login?expired=true'
      }
    } else if (status === 403) {
      // Permission denied: do not log out the user, attach a readable message
      const customError: ApiErrorDetail = {
        status: 403,
        message: serverMessage || 'Access forbidden: Your role lacks permission for this operation.',
        details: error.response?.data?.details,
      }
      return Promise.reject(customError)
    } else if (!error.response) {
      // Network failure or backend unavailability
      const customError: ApiErrorDetail = {
        status: 0,
        message: 'Backend server is unreachable. Please verify Spring Boot is running on port 8080.',
      }
      return Promise.reject(customError)
    }

    const customError: ApiErrorDetail = {
      status,
      message: serverMessage || error.message || 'An unexpected error occurred.',
      details: error.response?.data?.details,
    }
    return Promise.reject(customError)
  }
)


