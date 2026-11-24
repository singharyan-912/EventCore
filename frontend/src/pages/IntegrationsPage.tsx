import { useState, useEffect, useCallback } from 'react'
import {
  Globe,
  Plus,
  RefreshCw,
  Trash2,
  Activity,
  Lock,
  Clock,
  Shield,
  AlertTriangle,
  CheckCircle2,
  XCircle,
} from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Modal } from '../components/common/Modal'
import { EmptyState } from '../components/common/EmptyState'
import { integrationService } from '../services/integrationService'
import { tokenStorage } from '../utils/tokenStorage'
import type {
  IntegrationResponse,
  CreateIntegrationRequest,
  ConnectionTestResponse,
} from '../types/integrations'
import type { Role } from '../types/auth'

export function IntegrationsPage() {
  const user = tokenStorage.getUser()
  const userRole = (user?.role as Role) || 'VIEWER'
  const canManage = userRole === 'ADMIN' || userRole === 'OPERATOR'

  const [integrations, setIntegrations] = useState<IntegrationResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [formData, setFormData] = useState<CreateIntegrationRequest>({
    name: '',
    baseUrl: 'https://',
    supportedOperationTypes: 'APPOINTMENT_CREATE',
    authType: 'BEARER_TOKEN',
    authHeaderName: 'Authorization',
    secret: '',
    timeoutMs: 5000,
  })
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [testingId, setTestingId] = useState<number | null>(null)
  const [testResult, setTestResult] = useState<ConnectionTestResponse | null>(null)

  const [deleteTarget, setDeleteTarget] = useState<IntegrationResponse | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const loadIntegrations = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await integrationService.listIntegrations()
      setIntegrations(data)
    } catch (err: unknown) {
      const e = err as { message?: string }
      setError(e?.message || 'Failed to load external integrations.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => { loadIntegrations() }, [loadIntegrations])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!formData.name.trim() || !formData.baseUrl.trim()) return
    setIsSubmitting(true)
    setCreateError(null)
    try {
      await integrationService.createIntegration({
        ...formData,
        name: formData.name.trim(),
        baseUrl: formData.baseUrl.trim(),
        supportedOperationTypes: formData.supportedOperationTypes.trim().toUpperCase(),
        secret: formData.secret?.trim() || undefined,
        timeoutMs: Number(formData.timeoutMs) || 5000,
      })
      setIsCreateOpen(false)
      setFormData({ name: '', baseUrl: 'https://', supportedOperationTypes: 'APPOINTMENT_CREATE', authType: 'BEARER_TOKEN', authHeaderName: 'Authorization', secret: '', timeoutMs: 5000 })
      await loadIntegrations()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string }
      setCreateError(e?.response?.data?.message || e?.message || 'Failed to register integration.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleTest = async (id: number) => {
    setTestingId(id)
    setTestResult(null)
    try {
      const res = await integrationService.testConnection(id)
      setTestResult(res)
      await loadIntegrations()
    } catch (err: unknown) {
      const e = err as { message?: string }
      setTestResult({ integrationId: id, successful: false, message: e?.message || 'Connection test failed', testedAt: new Date().toISOString() })
    } finally {
      setTestingId(null)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    setIsDeleting(true)
    try {
      await integrationService.deleteIntegration(deleteTarget.id)
      setDeleteTarget(null)
      await loadIntegrations()
    } catch (err: unknown) {
      const e = err as { message?: string }
      setError(e?.message || 'Failed to delete integration.')
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="External Integrations"
        description="Connect salon, reservation, or e-commerce backends to ReliableFlow for reliable operation dispatch."
        actions={
          <div className="flex items-center gap-3">
            <Button variant="outline" size="sm" onClick={loadIntegrations} disabled={isLoading}>
              <RefreshCw className={`w-4 h-4 mr-1.5 ${isLoading ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
            {canManage && (
              <Button variant="primary" size="sm" onClick={() => setIsCreateOpen(true)}>
                <Plus className="w-4 h-4 mr-1.5" />
                Add Integration
              </Button>
            )}
          </div>
        }
      />

      {testResult && (
        <div className={`p-4 rounded-lg border flex items-center justify-between ${testResult.successful ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300' : 'bg-rose-500/10 border-rose-500/30 text-rose-300'}`}>
          <div className="flex items-center gap-3">
            {testResult.successful
              ? <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
              : <XCircle className="w-5 h-5 text-rose-400 shrink-0" />}
            <div>
              <p className="text-sm font-medium">{testResult.message}</p>
              <p className="text-xs opacity-75">Tested at: {new Date(testResult.testedAt).toLocaleTimeString()}</p>
            </div>
          </div>
          <Button variant="ghost" size="sm" onClick={() => setTestResult(null)}>Dismiss</Button>
        </div>
      )}

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Registered Adapters</CardTitle>
              <CardDescription>Tenant-scoped endpoints with AES-256 encrypted credentials and SSRF destination validation.</CardDescription>
            </div>
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <Shield className="w-4 h-4 text-emerald-400" />
              <span>SSRF Protected</span>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="py-12 flex flex-col items-center justify-center text-slate-400 gap-3">
              <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
              <p className="text-sm">Loading integrations...</p>
            </div>
          ) : error ? (
            <div className="py-8 text-center text-rose-400">
              <AlertTriangle className="w-8 h-8 mx-auto mb-2 opacity-80" />
              <p className="text-sm">{error}</p>
            </div>
          ) : integrations.length === 0 ? (
            <EmptyState
              icon={<Globe className="h-5 w-5" />}
              title="No external integrations configured"
              description="Register an external HTTP service to forward business operations."
              actionLabel={canManage ? 'Add Integration' : undefined}
              onAction={canManage ? () => setIsCreateOpen(true) : undefined}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-800/60 text-xs uppercase tracking-wider text-slate-400 border-b border-slate-700/60">
                  <tr>
                    <th className="py-3 px-4">Name / ID</th>
                    <th className="py-3 px-4">Base URL</th>
                    <th className="py-3 px-4">Supported Operations</th>
                    <th className="py-3 px-4">Authentication</th>
                    <th className="py-3 px-4">Last Health</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {integrations.map((item) => (
                    <tr key={item.id} className="hover:bg-slate-800/40 transition-colors">
                      <td className="py-3 px-4">
                        <div className="font-medium text-slate-100">{item.name}</div>
                        <div className="text-xs text-slate-400">#{item.id}</div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-mono text-xs text-cyan-300 max-w-xs truncate" title={item.baseUrl}>{item.baseUrl}</div>
                        <div className="text-xs text-slate-400 flex items-center gap-1 mt-0.5">
                          <Clock className="w-3 h-3" />
                          <span>{item.timeoutMs}ms</span>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {item.supportedOperationTypes.split(',').map((op) => (
                            <Badge key={op} variant="neutral">{op.trim()}</Badge>
                          ))}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-1.5 text-xs text-slate-300">
                          <Lock className="w-3.5 h-3.5 text-amber-400" />
                          <span>{item.authType}</span>
                        </div>
                        {item.secretPrefix && (
                          <div className="text-xs font-mono text-slate-400 mt-0.5">{item.secretPrefix}••••••••</div>
                        )}
                      </td>
                      <td className="py-3 px-4">
                        {item.lastConnectionStatus === 'CONNECTED' ? (
                          <Badge variant="accent">Connected</Badge>
                        ) : item.lastConnectionStatus === 'FAILED' ? (
                          <Badge variant="error">Failed</Badge>
                        ) : (
                          <span className="text-xs text-slate-500">Untested</span>
                        )}
                        {item.lastConnectionAt && (
                          <div className="text-xs text-slate-400 mt-0.5">{new Date(item.lastConnectionAt).toLocaleTimeString()}</div>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Button variant="ghost" size="sm" disabled={testingId === item.id || !canManage} onClick={() => handleTest(item.id)}>
                            <Activity className={`w-3.5 h-3.5 mr-1 ${testingId === item.id ? 'animate-pulse text-cyan-400' : ''}`} />
                            {testingId === item.id ? 'Testing...' : 'Test'}
                          </Button>
                          {canManage && (
                            <Button variant="ghost" size="sm" className="text-rose-400 hover:text-rose-300 hover:bg-rose-500/10" onClick={() => setDeleteTarget(item)}>
                              <Trash2 className="w-3.5 h-3.5" />
                            </Button>
                          )}
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

      <Modal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} title="Add External Integration">
        <form onSubmit={handleCreate} className="space-y-4">
          {createError && (
            <div className="p-3 rounded bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs">{createError}</div>
          )}
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Integration Name *</label>
            <input type="text" required placeholder="e.g. Salon Booking System" value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Destination URL (HTTPS only) *</label>
            <input type="url" required placeholder="https://api.external.com/appointments" value={formData.baseUrl}
              onChange={(e) => setFormData({ ...formData, baseUrl: e.target.value })}
              className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-cyan-500" />
            <p className="text-xs text-slate-400 mt-1">Loopback, private networks, and cloud metadata endpoints are blocked.</p>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Supported Operation Types *</label>
            <input type="text" required placeholder="APPOINTMENT_CREATE, INVENTORY_DEDUCT" value={formData.supportedOperationTypes}
              onChange={(e) => setFormData({ ...formData, supportedOperationTypes: e.target.value })}
              className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-cyan-500" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Auth Type</label>
              <select value={formData.authType} onChange={(e) => setFormData({ ...formData, authType: e.target.value })}
                className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500">
                <option value="NONE">None</option>
                <option value="BEARER_TOKEN">Bearer Token</option>
                <option value="API_KEY_HEADER">API Key Header</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Timeout (ms)</label>
              <input type="number" min="1000" max="30000" value={formData.timeoutMs}
                onChange={(e) => setFormData({ ...formData, timeoutMs: Number(e.target.value) })}
                className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500" />
            </div>
          </div>
          {formData.authType === 'API_KEY_HEADER' && (
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Header Name</label>
              <input type="text" placeholder="X-API-Key" value={formData.authHeaderName}
                onChange={(e) => setFormData({ ...formData, authHeaderName: e.target.value })}
                className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-cyan-500" />
            </div>
          )}
          {formData.authType !== 'NONE' && (
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Credential Secret</label>
              <input type="password" placeholder="sk_live_••••••••" value={formData.secret}
                onChange={(e) => setFormData({ ...formData, secret: e.target.value })}
                className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-cyan-500" />
              <p className="text-xs text-amber-400/90 mt-1">Encrypted with AES-256-GCM. Never returned in read responses.</p>
            </div>
          )}
          <div className="flex justify-end gap-3 pt-3 border-t border-slate-700/60">
            <Button variant="ghost" type="button" onClick={() => setIsCreateOpen(false)}>Cancel</Button>
            <Button variant="primary" type="submit" isLoading={isSubmitting}>
              {isSubmitting ? 'Registering...' : 'Register Integration'}
            </Button>
          </div>
        </form>
      </Modal>

      <Modal isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} title="Delete Integration">
        <div className="space-y-4">
          <p className="text-sm text-slate-300">
            Delete <span className="font-semibold text-slate-100">{deleteTarget?.name}</span>?
          </p>
          <p className="text-xs text-rose-400">Operations mapped to this integration will fail or fall back to internal processing.</p>
          <div className="flex justify-end gap-3 pt-3 border-t border-slate-700/60">
            <Button variant="ghost" onClick={() => setDeleteTarget(null)}>Cancel</Button>
            <Button variant="danger" isLoading={isDeleting} onClick={handleDelete}>
              {isDeleting ? 'Deleting...' : 'Confirm Delete'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
