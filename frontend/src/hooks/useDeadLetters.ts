import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { deadLetterService } from '../services/deadLetterService'
import type { DeadLetterResponse } from '../types/events'
import type { PageResponse } from '../types/api'

export const DLQ_KEYS = {
  all: ['dead-letters'] as const,
  lists: () => [...DLQ_KEYS.all, 'list'] as const,
  list: (page: number, size: number) => [...DLQ_KEYS.lists(), { page, size }] as const,
  details: () => [...DLQ_KEYS.all, 'detail'] as const,
  detail: (key: string) => [...DLQ_KEYS.details(), key] as const,
}

export function useDeadLetters(page = 0, size = 20) {
  const query = useQuery<PageResponse<DeadLetterResponse>, Error>({
    queryKey: DLQ_KEYS.list(page, size),
    queryFn: () => deadLetterService.listDeadLetters(page, size),
    staleTime: 10_000,
    retry: (failureCount, error: unknown) => {
      // Do not retry on 403 Forbidden
      if ((error as { status?: number })?.status === 403) return false
      return failureCount < 2
    },
  })

  return query
}

export function useDeadLetterDetail(idempotencyKey: string | null) {
  return useQuery<DeadLetterResponse, Error>({
    queryKey: DLQ_KEYS.detail(idempotencyKey ?? ''),
    queryFn: () => deadLetterService.getDeadLetter(idempotencyKey!),
    enabled: Boolean(idempotencyKey),
    staleTime: 10_000,
  })
}

export function useReprocessDeadLetter() {
  const queryClient = useQueryClient()

  return useMutation<DeadLetterResponse, Error, string>({
    mutationFn: (idempotencyKey: string) => deadLetterService.reprocessDeadLetter(idempotencyKey),
    onSuccess: (data) => {
      // Invalidate DLQ, operations events, and dashboard queries
      queryClient.invalidateQueries({ queryKey: DLQ_KEYS.all })
      queryClient.invalidateQueries({ queryKey: ['events'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      queryClient.setQueryData(DLQ_KEYS.detail(data.idempotencyKey), data)
    },
  })
}

