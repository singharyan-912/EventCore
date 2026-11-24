import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiKeyService } from '../services/apiKeyService'
import type { ApiKeyResponse, CreateApiKeyRequest, CreateApiKeyResponse } from '../types/apiKey'

export const API_KEY_QUERY_KEYS = {
  all: ['api-keys'] as const,
}

export function useApiKeys() {
  const queryClient = useQueryClient()

  const listQuery = useQuery<ApiKeyResponse[], Error>({
    queryKey: API_KEY_QUERY_KEYS.all,
    queryFn: () => apiKeyService.listApiKeys(),
    staleTime: 15_000,
  })

  const createMutation = useMutation<CreateApiKeyResponse, Error, CreateApiKeyRequest>({
    mutationFn: (req) => apiKeyService.createApiKey(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: API_KEY_QUERY_KEYS.all })
    },
  })

  const revokeMutation = useMutation<ApiKeyResponse, Error, number>({
    mutationFn: (id) => apiKeyService.revokeApiKey(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: API_KEY_QUERY_KEYS.all })
    },
  })

  const rotateMutation = useMutation<CreateApiKeyResponse, Error, number>({
    mutationFn: (id) => apiKeyService.rotateApiKey(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: API_KEY_QUERY_KEYS.all })
    },
  })

  return {
    keys: listQuery.data ?? [],
    isLoading: listQuery.isLoading,
    isFetching: listQuery.isFetching,
    error: listQuery.error,
    createKey: createMutation.mutateAsync,
    isCreating: createMutation.isPending,
    revokeKey: revokeMutation.mutateAsync,
    isRevoking: revokeMutation.isPending,
    rotateKey: rotateMutation.mutateAsync,
    isRotating: rotateMutation.isPending,
    refetch: listQuery.refetch,
  }
}
