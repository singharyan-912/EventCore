import { useState, useEffect, useCallback } from 'react'
import {
  Scale,
  RefreshCw,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  ExternalLink,
  ShieldAlert,
} from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Modal } from '../components/common/Modal'
import { EmptyState } from '../components/common/EmptyState'
import { reconciliationService } from '../services/reconciliationService'
import { tokenStorage } from '../utils/tokenStorage'
import type { ReconciliationDetailResponse } from '../types/reconciliation'

export function ReconciliationPage() {
  const [items, setItems] = useState<ReconciliationDetailResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Inspection & Manual Resolution Modal state
  const [selectedItem, setSelectedItem] = useState<ReconciliationDetailResponse | null>(null)
  const [isResolving, setIsResolving] = useState(false)
  const [isVerifying, setIsVerifying] = useState(false)
  const [resolution, setResolution] = useState<'SUCCESS' | 'FAILURE'>('SUCCESS')
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState('')
  const [actionMessage, setActionMessage] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  const currentUser = tokenStorage.getUser()
  const canManage = currentUser?.role === 'ADMIN' || currentUser?.role === 'OPERATOR'

  const loadData = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const res = await reconciliationService.listReconciliations(0, 50)
      setItems(res.content)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load unresolved operations'
      setError(msg)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleVerify = async (key: string) => {
    setIsVerifying(true)
    setActionMessage(null)
    try {
      const res = await reconciliationService.verifyAutomatically(key)
      setActionMessage({ type: 'success', message: res.message })
      await loadData()
      if (selectedItem?.idempotencyKey === key) {
        setSelectedItem(null)
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Verification failed'
      setActionMessage({ type: 'error', message: msg })
    } finally {
      setIsVerifying(false)
    }
  }

  const handleManualResolve = async () => {
    if (!selectedItem || !reason.trim()) return
    setIsResolving(true)
    setActionMessage(null)
    try {
      const res = await reconciliationService.resolveManually(selectedItem.idempotencyKey, {
        resolution,
        reason: reason.trim(),
        notes: notes.trim() || undefined,
      })
      setActionMessage({ type: 'success', message: res.message })
      setSelectedItem(null)
      setReason('')
      setNotes('')
      await loadData()
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Resolution failed'
      setActionMessage({ type: 'error', message: msg })
    } finally {
      setIsResolving(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Operation Reconciliation"
        description="Safely inspect and resolve ambiguous external outcomes without blind retries or duplicate side effects."
        badge={
          <Badge variant={items.length > 0 ? 'warning' : 'accent'}>
            {items.length} Unresolved
          </Badge>
        }
        actions={
          <Button variant="outline" size="sm" onClick={loadData} disabled={isLoading}>
            <RefreshCw className={`w-4 h-4 mr-2 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        }
      />

      {actionMessage && (
        <div
          className={`p-4 rounded-lg flex items-center gap-3 text-sm border ${
            actionMessage.type === 'success'
              ? 'bg-emerald-950/40 border-emerald-700/60 text-emerald-300'
              : 'bg-rose-950/40 border-rose-700/60 text-rose-300'
          }`}
        >
          {actionMessage.type === 'success' ? (
            <CheckCircle2 className="w-5 h-5 shrink-0 text-emerald-400" />
          ) : (
            <AlertTriangle className="w-5 h-5 shrink-0 text-rose-400" />
          )}
          <span>{actionMessage.message}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Uncertain Operations</CardTitle>
              <CardDescription>
                Operations in <code className="text-amber-300 font-mono text-xs">RECONCILIATION_REQUIRED</code> state.
              </CardDescription>
            </div>
            <div className="flex items-center gap-2 text-xs text-amber-400">
              <ShieldAlert className="w-4 h-4" />
              <span>Automatic Retries Suspended</span>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="py-12 flex flex-col items-center justify-center text-slate-400 gap-3">
              <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
              <p className="text-sm">Loading reconciliation records...</p>
            </div>
          ) : error ? (
            <div className="py-8 text-center text-rose-400">
              <AlertTriangle className="w-8 h-8 mx-auto mb-2 opacity-80" />
              <p className="text-sm">{error}</p>
            </div>
          ) : items.length === 0 ? (
            <EmptyState
              icon={<Scale className="h-5 w-5" />}
              title="No operations requiring reconciliation"
              description="All external integrations and asynchronous events are currently in definite states."
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-800/60 text-xs uppercase tracking-wider text-slate-400 border-b border-slate-700/60">
                  <tr>
                    <th className="py-3 px-4">Idempotency Key</th>
                    <th className="py-3 px-4">Operation Type</th>
                    <th className="py-3 px-4">Resource ID</th>
                    <th className="py-3 px-4">Ambiguous Reason</th>
                    <th className="py-3 px-4">Last Attempt</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {items.map((item) => (
                    <tr key={item.idempotencyKey} className="hover:bg-slate-800/40 transition-colors">
                      <td className="py-3 px-4 font-mono text-xs text-cyan-300">
                        {item.idempotencyKey}
                      </td>
                      <td className="py-3 px-4">
                        <Badge variant="neutral">{item.eventType}</Badge>
                      </td>
                      <td className="py-3 px-4 font-mono text-xs text-slate-300">
                        {item.resourceId || '—'}
                      </td>
                      <td className="py-3 px-4 text-xs text-rose-300 max-w-xs truncate" title={item.lastFailureReason || ''}>
                        {item.lastFailureReason || 'External outcome uncertain'}
                      </td>
                      <td className="py-3 px-4 text-xs text-slate-400">
                        {item.lastAttemptAt ? new Date(item.lastAttemptAt).toLocaleString() : '—'}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={isVerifying}
                            onClick={() => handleVerify(item.idempotencyKey)}
                          >
                            <ExternalLink className="w-3.5 h-3.5 mr-1 text-cyan-400" />
                            Verify
                          </Button>
                          <Button
                            variant="primary"
                            size="sm"
                            disabled={!canManage}
                            onClick={() => {
                              setSelectedItem(item)
                              setReason('')
                              setNotes('')
                              setResolution('SUCCESS')
                            }}
                          >
                            Resolve
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Manual Resolution & Inspection Modal */}
      <Modal
        isOpen={!!selectedItem}
        onClose={() => setSelectedItem(null)}
        title="Resolve Uncertain Operation"
      >
        {selectedItem && (
          <div className="space-y-4">
            <div className="p-3 bg-slate-800/60 rounded border border-slate-700/60 text-xs space-y-2">
              <div className="flex justify-between">
                <span className="text-slate-400">Idempotency Key:</span>
                <span className="font-mono text-cyan-300">{selectedItem.idempotencyKey}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Operation:</span>
                <span className="font-semibold text-slate-200">{selectedItem.eventType}</span>
              </div>
              <div className="text-slate-400">Ambiguous Failure Reason:</div>
              <div className="text-rose-300 font-mono text-[11px] bg-slate-900/80 p-2 rounded max-h-20 overflow-y-auto">
                {selectedItem.lastFailureReason || 'Uncertain outcome during external execution'}
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-xs font-medium text-slate-300">Resolution Decision</label>
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => setResolution('SUCCESS')}
                  className={`p-3 rounded border text-left text-xs transition-colors flex items-center gap-2 ${
                    resolution === 'SUCCESS'
                      ? 'bg-emerald-950/40 border-emerald-500 text-emerald-200'
                      : 'bg-slate-800/40 border-slate-700 text-slate-400'
                  }`}
                >
                  <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  <span>Mark as Succeeded</span>
                </button>
                <button
                  type="button"
                  onClick={() => setResolution('FAILURE')}
                  className={`p-3 rounded border text-left text-xs transition-colors flex items-center gap-2 ${
                    resolution === 'FAILURE'
                      ? 'bg-rose-950/40 border-rose-500 text-rose-200'
                      : 'bg-slate-800/40 border-slate-700 text-slate-400'
                  }`}
                >
                  <XCircle className="w-4 h-4 text-rose-400" />
                  <span>Mark as Failed</span>
                </button>
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-300">
                Resolution Reason <span className="text-rose-400">*</span>
              </label>
              <input
                type="text"
                placeholder="e.g., Verified external provider dashboard confirmed order #4912 was charged"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="w-full bg-slate-900/80 border border-slate-700 rounded px-3 py-2 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium text-slate-300">Additional Notes (Optional)</label>
              <textarea
                rows={2}
                placeholder="Any additional audit context..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                className="w-full bg-slate-900/80 border border-slate-700 rounded px-3 py-2 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500"
              />
            </div>

            <div className="flex justify-end gap-3 pt-3 border-t border-slate-700/60">
              <Button variant="ghost" onClick={() => setSelectedItem(null)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                isLoading={isResolving}
                disabled={!reason.trim()}
                onClick={handleManualResolve}
              >
                {isResolving ? 'Recording...' : 'Confirm Resolution'}
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
