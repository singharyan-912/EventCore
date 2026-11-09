import { RefreshCw, ShieldAlert } from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { AccessDenied } from '../components/common/AccessDenied'
import { AuditFilterBar } from '../components/audit/AuditFilterBar'
import { AuditTable } from '../components/audit/AuditTable'
import { AuditDetailModal } from '../components/audit/AuditDetailModal'
import { useAuditLogs } from '../hooks/useAuditLogs'
import { tokenStorage } from '../utils/tokenStorage'

export function AuditLogsPage() {
  const user = tokenStorage.getUser()
  const isAdmin = user?.role === 'ADMIN'

  const {
    page,
    size,
    actionType,
    setActionType,
    targetIdentifier,
    setTargetIdentifier,
    outcome,
    setOutcome,
    selectedLog,
    setSelectedLog,
    data,
    isLoading,
    isFetching,
    error,
    applyFilters,
    resetFilters,
    handlePageChange,
    refreshAll,
  } = useAuditLogs()

  // If user is not an administrator, show access restricted view
  if (!isAdmin) {
    return (
      <AccessDenied
        requiredRole="ADMIN"
        featureName="Security & Operational Audit Logs"
      />
    )
  }

  const isForbidden = (error as { status?: number })?.status === 403

  return (
    <div className="space-y-6">
      <PageHeader
        title="Security & Operational Audit Logs"
        description="Immutable audit trail of authentication events, user role modifications, and operator recovery actions."
        breadcrumbs={[{ label: 'Audit Logs' }]}
        badge={<Badge variant="accent">ADMIN ACCESS</Badge>}
        actions={
          <Button variant="outline" size="sm" onClick={refreshAll} disabled={isFetching}>
            <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh Logs
          </Button>
        }
      />

      {isForbidden && (
        <div className="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
          <ShieldAlert className="h-5 w-5 text-rose-600 dark:text-rose-400 shrink-0 mt-0.5" />
          <div>
            <h4 className="font-semibold">Access Forbidden (403)</h4>
            <p className="mt-0.5">
              The backend returned 403 Forbidden for <code>/api/v1/operations/audit-logs</code>. Administrator credentials required.
            </p>
          </div>
        </div>
      )}

      {/* Filter Bar */}
      {!isForbidden && (
        <AuditFilterBar
          actionType={actionType}
          onActionTypeChange={setActionType}
          targetIdentifier={targetIdentifier}
          onTargetIdentifierChange={setTargetIdentifier}
          outcome={outcome}
          onOutcomeChange={setOutcome}
          onApply={applyFilters}
          onReset={resetFilters}
          isLoading={isLoading}
        />
      )}

      {/* Audit Records Table */}
      {!isForbidden && (
        <AuditTable
          logs={data?.content ?? []}
          page={page}
          totalPages={data?.totalPages ?? 0}
          totalElements={data?.totalElements ?? 0}
          pageSize={size}
          onPageChange={handlePageChange}
          onInspect={setSelectedLog}
          isLoading={isLoading}
        />
      )}

      {/* Inspection Modal */}
      <AuditDetailModal
        log={selectedLog}
        isOpen={Boolean(selectedLog)}
        onClose={() => setSelectedLog(null)}
      />
    </div>
  )
}
