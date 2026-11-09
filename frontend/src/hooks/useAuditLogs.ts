import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { auditService, type AuditLogSearchParams } from '../services/auditService'
import type { PageResponse } from '../types/api'
import type { OperationalAuditLogResponse } from '../types/audit'

export const AUDIT_KEYS = {
  all: ['audit-logs'] as const,
  list: (params: AuditLogSearchParams) => [...AUDIT_KEYS.all, 'list', params] as const,
}

export function useAuditLogs(initialSize = 20) {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(initialSize)
  const [actionType, setActionType] = useState('')
  const [targetIdentifier, setTargetIdentifier] = useState('')
  const [outcome, setOutcome] = useState('ALL')
  const [selectedLog, setSelectedLog] = useState<OperationalAuditLogResponse | null>(null)

  // Applied filter state
  const [appliedFilters, setAppliedFilters] = useState<AuditLogSearchParams>({
    page: 0,
    size: initialSize,
    actionType: '',
    targetIdentifier: '',
    outcome: 'ALL',
  })

  const queryClient = useQueryClient()

  const query = useQuery<PageResponse<OperationalAuditLogResponse>, Error>({
    queryKey: AUDIT_KEYS.list(appliedFilters),
    queryFn: () => auditService.getAuditLogs(appliedFilters),
    staleTime: 15_000,
    retry: (failureCount, error: unknown) => {
      // Never retry on 403 Forbidden
      if ((error as { status?: number })?.status === 403) return false
      return failureCount < 2
    },
  })

  const applyFilters = () => {
    setPage(0)
    setAppliedFilters({
      page: 0,
      size,
      actionType,
      targetIdentifier,
      outcome,
    })
  }

  const resetFilters = () => {
    setActionType('')
    setTargetIdentifier('')
    setOutcome('ALL')
    setPage(0)
    setAppliedFilters({
      page: 0,
      size,
      actionType: '',
      targetIdentifier: '',
      outcome: 'ALL',
    })
  }

  const handlePageChange = (newPage: number) => {
    setPage(newPage)
    setAppliedFilters((prev) => ({ ...prev, page: newPage }))
  }

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: AUDIT_KEYS.all })
  }

  return {
    page,
    size,
    setSize,
    actionType,
    setActionType,
    targetIdentifier,
    setTargetIdentifier,
    outcome,
    setOutcome,
    selectedLog,
    setSelectedLog,
    data: query.data,
    isLoading: query.isLoading,
    isFetching: query.isFetching,
    error: query.error,
    applyFilters,
    resetFilters,
    handlePageChange,
    refreshAll,
  }
}
