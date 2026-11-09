import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { userService } from '../services/userService'
import type { PageResponse } from '../types/api'
import type { UserResponse, CreateUserRequest } from '../types/user'
import type { Role, UserStatus } from '../types/auth'

export const USER_KEYS = {
  all: ['users'] as const,
  list: (page: number, size: number) => [...USER_KEYS.all, 'list', { page, size }] as const,
}

export function useUsers(initialPage = 0, initialSize = 20) {
  const [page, setPage] = useState(initialPage)
  const [size, setSize] = useState(initialSize)
  const [selectedUser, setSelectedUser] = useState<UserResponse | null>(null)

  const queryClient = useQueryClient()

  const listQuery = useQuery<PageResponse<UserResponse>, Error>({
    queryKey: USER_KEYS.list(page, size),
    queryFn: () => userService.listUsers(page, size),
    staleTime: 15_000,
    retry: (failureCount, error: unknown) => {
      if ((error as { status?: number })?.status === 403) return false
      return failureCount < 2
    },
  })

  const createMutation = useMutation<UserResponse, Error, CreateUserRequest>({
    mutationFn: (req) => userService.createUser(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USER_KEYS.all })
      queryClient.invalidateQueries({ queryKey: ['audit-logs'] })
    },
  })

  const updateRoleMutation = useMutation<UserResponse, Error, { id: number; role: Role }>({
    mutationFn: ({ id, role }) => userService.updateUserRole(id, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USER_KEYS.all })
      queryClient.invalidateQueries({ queryKey: ['audit-logs'] })
    },
  })

  const updateStatusMutation = useMutation<UserResponse, Error, { id: number; status: UserStatus }>({
    mutationFn: ({ id, status }) => userService.updateUserStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: USER_KEYS.all })
      queryClient.invalidateQueries({ queryKey: ['audit-logs'] })
    },
  })

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: USER_KEYS.all })
  }

  return {
    page,
    setPage,
    size,
    setSize,
    selectedUser,
    setSelectedUser,
    usersPage: listQuery.data,
    isLoading: listQuery.isLoading,
    isFetching: listQuery.isFetching,
    error: listQuery.error,
    createUser: createMutation.mutateAsync,
    isCreating: createMutation.isPending,
    createError: createMutation.error,
    updateRole: updateRoleMutation.mutateAsync,
    isUpdatingRole: updateRoleMutation.isPending,
    updateRoleError: updateRoleMutation.error,
    updateStatus: updateStatusMutation.mutateAsync,
    isUpdatingStatus: updateStatusMutation.isPending,
    updateStatusError: updateStatusMutation.error,
    refreshAll,
  }
}
