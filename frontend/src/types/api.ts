export interface ErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  details?: string[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

