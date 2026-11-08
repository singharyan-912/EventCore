import { RefreshCw, ShieldCheck } from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { OutboxMetricsCards } from '../components/outbox/OutboxMetricsCards'
import { OutboxTable } from '../components/outbox/OutboxTable'
import { OutboxDetailModal } from '../components/outbox/OutboxDetailModal'
import { useOutbox } from '../hooks/useOutbox'

export function OutboxPage() {
  const {
    page,
    setPage,
    size,
    statusFilter,
    setStatusFilter,
    searchKey,
    setSearchKey,
    selectedRecord,
    setSelectedRecord,
    summary,
    outboxRecords,
    rawEventsPage,
    isLoading,
    isFetching,
    refreshAll,
  } = useOutbox()

  return (
    <div className="space-y-6">
      <PageHeader
        title="Transactional Outbox Monitor"
        description="Monitor ACID outbox event publications, batch polling workers, and Apache Kafka broker delivery status."
        breadcrumbs={[{ label: 'Outbox Monitor' }]}
        actions={
          <Button variant="outline" size="sm" onClick={refreshAll} disabled={isFetching}>
            <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh Outbox
          </Button>
        }
      />

      {/* Architecture Assurance Banner */}
      <div className="flex items-center gap-2.5 rounded-lg border border-slate-200 bg-slate-50/80 p-3 text-xs text-slate-700 dark:border-slate-800 dark:bg-slate-900/60 dark:text-slate-300">
        <ShieldCheck className="h-4 w-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
        <span>
          <strong>Zero Message Loss Architecture:</strong> Outbox records are written in the same PostgreSQL transaction as event updates and published asynchronously via a background worker using <code>SKIP LOCKED</code>.
        </span>
      </div>

      {/* Metrics Cards */}
      <OutboxMetricsCards summary={summary} isLoading={isLoading} />

      {/* Outbox Table */}
      <OutboxTable
        records={outboxRecords}
        statusFilter={statusFilter}
        onStatusChange={setStatusFilter}
        searchKey={searchKey}
        onSearchChange={setSearchKey}
        onInspect={setSelectedRecord}
        page={page}
        totalPages={rawEventsPage?.totalPages ?? 0}
        totalElements={rawEventsPage?.totalElements ?? 0}
        pageSize={size}
        onPageChange={setPage}
        isLoading={isLoading}
      />

      {/* Record Inspection Modal */}
      <OutboxDetailModal
        event={selectedRecord}
        isOpen={Boolean(selectedRecord)}
        onClose={() => setSelectedRecord(null)}
      />
    </div>
  )
}
