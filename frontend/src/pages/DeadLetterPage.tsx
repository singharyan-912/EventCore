import { useState } from 'react'
import { RefreshCw, ShieldAlert, Search } from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Badge } from '../components/common/Badge'
import { Button } from '../components/common/Button'
import { Input } from '../components/common/Input'
import { DeadLetterTable } from '../components/dead-letters/DeadLetterTable'
import { DeadLetterDetailModal } from '../components/dead-letters/DeadLetterDetailModal'
import { ReprocessConfirmModal } from '../components/dead-letters/ReprocessConfirmModal'
import { useDeadLetters, useReprocessDeadLetter } from '../hooks/useDeadLetters'
import { tokenStorage } from '../utils/tokenStorage'
import type { DeadLetterResponse } from '../types/events'

export function DeadLetterPage() {
  const [page, setPage] = useState(0)
  const [pageSize] = useState(20)
  const [search, setSearch] = useState('')
  const [selectedEvent, setSelectedEvent] = useState<DeadLetterResponse | null>(null)
  const [selectedKeys, setSelectedKeys] = useState<string[]>([])

  // Confirmation modal state
  const [confirmModalOpen, setConfirmModalOpen] = useState(false)
  const [targetKeys, setTargetKeys] = useState<string[]>([])
  const [isProcessing, setIsProcessing] = useState(false)
  const [progress, setProgress] = useState<{ current: number; total: number } | undefined>(undefined)
  const [resultMessage, setResultMessage] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  // Auth inspection
  const currentUser = tokenStorage.getUser()
  const userRole = currentUser?.role ?? 'VIEWER'
  const canReprocess = userRole === 'ADMIN' || userRole === 'OPERATOR'

  // Data fetching
  const { data: pageData, isLoading, isFetching, error, refetch } = useDeadLetters(page, pageSize)
  const reprocessMutation = useReprocessDeadLetter()

  const rawEvents = pageData?.content ?? []
  const filteredEvents = search
    ? rawEvents.filter(
        (e) =>
          e.idempotencyKey.toLowerCase().includes(search.toLowerCase()) ||
          e.eventType.toLowerCase().includes(search.toLowerCase()) ||
          e.resourceId.toLowerCase().includes(search.toLowerCase())
      )
    : rawEvents

  const isForbidden = (error as { status?: number })?.status === 403

  // Selection handlers
  const handleToggleSelect = (key: string) => {
    setSelectedKeys((prev) => (prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]))
  }

  const handleToggleSelectAll = () => {
    if (selectedKeys.length === filteredEvents.length) {
      setSelectedKeys([])
    } else {
      setSelectedKeys(filteredEvents.map((e) => e.idempotencyKey))
    }
  }

  // Reprocess trigger
  const handleOpenReprocess = (key: string) => {
    setTargetKeys([key])
    setResultMessage(null)
    setProgress(undefined)
    setConfirmModalOpen(true)
  }

  const handleOpenBulkReprocess = (keys: string[]) => {
    setTargetKeys(keys)
    setResultMessage(null)
    setProgress(undefined)
    setConfirmModalOpen(true)
  }

  const handleConfirmReprocess = async () => {
    if (targetKeys.length === 0) return
    setIsProcessing(true)
    setResultMessage(null)

    let successCount = 0
    let lastError: string | null = null

    for (let i = 0; i < targetKeys.length; i++) {
      const key = targetKeys[i]
      setProgress({ current: i + 1, total: targetKeys.length })
      try {
        await reprocessMutation.mutateAsync(key)
        successCount++
      } catch (err: unknown) {
        lastError = (err as { message?: string })?.message || 'Reprocessing rejected by backend'
      }
    }

    setIsProcessing(false)
    setProgress(undefined)

    if (successCount === targetKeys.length) {
      setResultMessage({
        type: 'success',
        message: `Successfully scheduled ${successCount} event${successCount > 1 ? 's' : ''} for immediate retry by RetryWorker.`,
      })
      setSelectedKeys([])
      refetch()
    } else {
      setResultMessage({
        type: 'error',
        message: `Processed ${successCount}/${targetKeys.length} events. Last error: ${lastError}`,
      })
      refetch()
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Retry & Dead Letter Queue (DLQ)"
        description="Inspect exhausted poison events, evaluate failure root causes, and trigger operator-initiated reprocessing."
        breadcrumbs={[{ label: 'Dead Letter Queue' }]}
        badge={
          <Badge variant={canReprocess ? 'warning' : 'neutral'}>
            {canReprocess ? `${userRole} ACCESS` : 'VIEW-ONLY ACCESS'}
          </Badge>
        }
        actions={
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh Queue
          </Button>
        }
      />

      {/* Role Notice for Viewer */}
      {!canReprocess && (
        <div className="flex items-center gap-2.5 rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300">
          <ShieldAlert className="h-4 w-4 text-amber-500 shrink-0" />
          <span>
            You are signed in as <strong>{currentUser?.username || 'Viewer'}</strong> with role{' '}
            <code className="font-semibold">{userRole}</code>. Event reprocessing is restricted to{' '}
            <strong>OPERATOR</strong> and <strong>ADMIN</strong> roles.
          </span>
        </div>
      )}

      {/* Backend 403 Forbidden Banner */}
      {isForbidden && (
        <div className="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
          <ShieldAlert className="h-5 w-5 text-rose-600 dark:text-rose-400 shrink-0 mt-0.5" />
          <div>
            <h4 className="font-semibold">Access Forbidden (403)</h4>
            <p className="mt-0.5">
              The backend returned 403 Forbidden for <code>/api/v1/admin/dead-letters</code>. Your role lacks administrative permissions to query the dead-letter queue.
            </p>
          </div>
        </div>
      )}

      {/* Search Bar */}
      {!isForbidden && (
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="w-full sm:w-80">
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter by key, type, or resource..."
              leftIcon={<Search className="h-4 w-4 text-slate-400" />}
            />
          </div>
          <div className="text-xs text-slate-500 dark:text-slate-400">
            Showing {filteredEvents.length} of {pageData?.totalElements ?? 0} dead-letter events
          </div>
        </div>
      )}

      {/* Main Table */}
      {!isForbidden && (
        <DeadLetterTable
          events={filteredEvents}
          page={page}
          totalPages={pageData?.totalPages ?? 0}
          totalElements={pageData?.totalElements ?? 0}
          pageSize={pageSize}
          onPageChange={setPage}
          onInspect={setSelectedEvent}
          onReprocess={handleOpenReprocess}
          onBulkReprocess={handleOpenBulkReprocess}
          selectedKeys={selectedKeys}
          onToggleSelect={handleToggleSelect}
          onToggleSelectAll={handleToggleSelectAll}
          canReprocess={canReprocess}
          isLoading={isLoading}
        />
      )}

      {/* Detail Modal */}
      <DeadLetterDetailModal
        event={selectedEvent}
        isOpen={Boolean(selectedEvent)}
        onClose={() => setSelectedEvent(null)}
        onReprocess={handleOpenReprocess}
        canReprocess={canReprocess}
      />

      {/* Reprocess Confirmation Modal */}
      <ReprocessConfirmModal
        isOpen={confirmModalOpen}
        onClose={() => setConfirmModalOpen(false)}
        onConfirm={handleConfirmReprocess}
        idempotencyKeys={targetKeys}
        isProcessing={isProcessing}
        currentProgress={progress}
        resultMessage={resultMessage}
      />
    </div>
  )
}
