import { useState, useEffect, useCallback } from 'react'
import {
  Webhook as WebhookIcon,
  Plus,
  RefreshCw,
  Trash2,
  KeyRound,
  RotateCw,
  Copy,
  Check,
  History,
  AlertTriangle,
  ShieldAlert,
} from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Modal } from '../components/common/Modal'
import { EmptyState } from '../components/common/EmptyState'
import { webhookService } from '../services/webhookService'
import { tokenStorage } from '../utils/tokenStorage'
import type {
  WebhookResponse,
  CreateWebhookRequest,
  WebhookDeliveryResponse,
} from '../types/webhooks'
import type { Role } from '../types/auth'

export function WebhooksPage() {
  const user = tokenStorage.getUser()
  const userRole = (user?.role as Role) || 'VIEWER'
  const canManage = userRole === 'ADMIN' || userRole === 'OPERATOR'

  const [webhooks, setWebhooks] = useState<WebhookResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [formData, setFormData] = useState<CreateWebhookRequest>({
    name: '',
    destinationUrl: 'https://',
    subscribedEvents: 'OPERATION_SUCCEEDED,OPERATION_FAILED,OPERATION_REQUIRES_RECONCILIATION',
  })
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [revealedSecret, setRevealedSecret] = useState<{ name: string; secret: string } | null>(null)
  const [hasCopiedSecret, setHasCopiedSecret] = useState(false)

  const [rotateTarget, setRotateTarget] = useState<WebhookResponse | null>(null)
  const [isRotating, setIsRotating] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<WebhookResponse | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  const [deliveryWebhook, setDeliveryWebhook] = useState<WebhookResponse | null>(null)
  const [deliveries, setDeliveries] = useState<WebhookDeliveryResponse[]>([])
  const [isLoadingDeliveries, setIsLoadingDeliveries] = useState(false)
  const [redeliveringId, setRedeliveringId] = useState<number | null>(null)

  const loadWebhooks = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const data = await webhookService.listWebhooks()
      setWebhooks(data)
    } catch (err: unknown) {
      const e = err as { message?: string }
      setError(e?.message || 'Failed to load webhooks.')
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => { loadWebhooks() }, [loadWebhooks])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!formData.name.trim() || !formData.destinationUrl.trim()) return
    setIsSubmitting(true)
    setCreateError(null)
    try {
      const created = await webhookService.createWebhook({
        name: formData.name.trim(),
        destinationUrl: formData.destinationUrl.trim(),
        subscribedEvents: formData.subscribedEvents.trim().toUpperCase(),
      })
      setIsCreateOpen(false)
      setFormData({ name: '', destinationUrl: 'https://', subscribedEvents: 'OPERATION_SUCCEEDED,OPERATION_FAILED,OPERATION_REQUIRES_RECONCILIATION' })
      if (created.signingSecret) {
        setRevealedSecret({ name: created.name, secret: created.signingSecret })
      }
      await loadWebhooks()
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string }
      setCreateError(e?.response?.data?.message || e?.message || 'Failed to create webhook.')
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleRotateSecret = async () => {
    if (!rotateTarget) return
    setIsRotating(true)
    try {
      const updated = await webhookService.rotateSecret(rotateTarget.id)
      setRotateTarget(null)
      if (updated.signingSecret) {
        setRevealedSecret({ name: updated.name, secret: updated.signingSecret })
      }
      await loadWebhooks()
    } catch (err: unknown) {
      const e = err as { message?: string }
      setError(e?.message || 'Failed to rotate webhook secret.')
    } finally {
      setIsRotating(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    setIsDeleting(true)
    try {
      await webhookService.deleteWebhook(deleteTarget.id)
      setDeleteTarget(null)
      await loadWebhooks()
    } catch (err: unknown) {
      const e = err as { message?: string }
      setError(e?.message || 'Failed to delete webhook.')
    } finally {
      setIsDeleting(false)
    }
  }

  const handleViewDeliveries = async (item: WebhookResponse) => {
    setDeliveryWebhook(item)
    setIsLoadingDeliveries(true)
    try {
      const list = await webhookService.listDeliveries(item.id)
      setDeliveries(list)
    } catch (err: unknown) {
      const e = err as { message?: string }
      setError(e?.message || 'Failed to load delivery history.')
    } finally {
      setIsLoadingDeliveries(false)
    }
  }

  const handleRedeliver = async (deliveryId: number) => {
    if (!deliveryWebhook) return
    setRedeliveringId(deliveryId)
    try {
      await webhookService.redeliver(deliveryWebhook.id, deliveryId)
      const list = await webhookService.listDeliveries(deliveryWebhook.id)
      setDeliveries(list)
    } catch (err: unknown) {
      const e = err as { message?: string }
      setError(e?.message || 'Failed to schedule redelivery.')
    } finally {
      setRedeliveringId(null)
    }
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    setHasCopiedSecret(true)
    setTimeout(() => setHasCopiedSecret(false), 2500)
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Webhook Notifications"
        description="Receive signed HTTP notifications for OPERATION_SUCCEEDED, OPERATION_FAILED, OPERATION_RETRYING, and OPERATION_REQUIRES_RECONCILIATION events."
        actions={
          <div className="flex items-center gap-3">
            <Button variant="outline" size="sm" onClick={loadWebhooks} disabled={isLoading}>
              <RefreshCw className={`w-4 h-4 mr-1.5 ${isLoading ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
            {canManage && (
              <Button variant="primary" size="sm" onClick={() => setIsCreateOpen(true)}>
                <Plus className="w-4 h-4 mr-1.5" />
                Register Webhook
              </Button>
            )}
          </div>
        }
      />

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Webhook Destinations</CardTitle>
              <CardDescription>
                Payloads signed with <code className="text-cyan-300 font-mono text-xs">X-ReliableFlow-Signature: t=...,v1=...</code> (HMAC-SHA256).
              </CardDescription>
            </div>
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <KeyRound className="w-4 h-4 text-amber-400" />
              <span>HMAC Signed</span>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="py-12 flex flex-col items-center justify-center text-slate-400 gap-3">
              <RefreshCw className="w-6 h-6 animate-spin text-cyan-400" />
              <p className="text-sm">Loading webhooks...</p>
            </div>
          ) : error ? (
            <div className="py-8 text-center text-rose-400">
              <AlertTriangle className="w-8 h-8 mx-auto mb-2 opacity-80" />
              <p className="text-sm">{error}</p>
            </div>
          ) : webhooks.length === 0 ? (
            <EmptyState
              icon={<WebhookIcon className="h-5 w-5" />}
              title="No webhooks registered"
              description="Register an HTTPS endpoint to receive lifecycle event notifications."
              actionLabel={canManage ? 'Register Webhook' : undefined}
              onAction={canManage ? () => setIsCreateOpen(true) : undefined}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-800/60 text-xs uppercase tracking-wider text-slate-400 border-b border-slate-700/60">
                  <tr>
                    <th className="py-3 px-4">Name / ID</th>
                    <th className="py-3 px-4">Destination URL</th>
                    <th className="py-3 px-4">Subscribed Events</th>
                    <th className="py-3 px-4">Secret Prefix</th>
                    <th className="py-3 px-4">Last Delivery</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {webhooks.map((item) => (
                    <tr key={item.id} className="hover:bg-slate-800/40 transition-colors">
                      <td className="py-3 px-4">
                        <div className="font-medium text-slate-100">{item.name}</div>
                        <div className="text-xs text-slate-400">#{item.id}</div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="font-mono text-xs text-cyan-300 max-w-xs truncate" title={item.destinationUrl}>{item.destinationUrl}</div>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {item.subscribedEvents.split(',').map((ev) => (
                            <Badge key={ev} variant="neutral">{ev.trim()}</Badge>
                          ))}
                        </div>
                      </td>
                      <td className="py-3 px-4 font-mono text-xs text-slate-300">
                        {item.secretPrefix ? `${item.secretPrefix}••••••••` : '••••••••'}
                      </td>
                      <td className="py-3 px-4">
                        {item.lastDeliveryStatus === 'SUCCESS' ? (
                          <Badge variant="accent">Success</Badge>
                        ) : item.lastDeliveryStatus ? (
                          <Badge variant="error">{item.lastDeliveryStatus}</Badge>
                        ) : (
                          <span className="text-xs text-slate-500">No deliveries</span>
                        )}
                        {item.lastDeliveryAt && (
                          <div className="text-xs text-slate-400 mt-0.5">{new Date(item.lastDeliveryAt).toLocaleTimeString()}</div>
                        )}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Button variant="ghost" size="sm" onClick={() => handleViewDeliveries(item)}>
                            <History className="w-3.5 h-3.5 mr-1" />
                            Logs
                          </Button>
                          {canManage && (
                            <>
                              <Button variant="ghost" size="sm" onClick={() => setRotateTarget(item)} title="Rotate signing secret">
                                <RotateCw className="w-3.5 h-3.5" />
                              </Button>
                              <Button variant="ghost" size="sm" className="text-rose-400 hover:text-rose-300 hover:bg-rose-500/10" onClick={() => setDeleteTarget(item)}>
                                <Trash2 className="w-3.5 h-3.5" />
                              </Button>
                            </>
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

      {/* Register Webhook Modal */}
      <Modal isOpen={isCreateOpen} onClose={() => setIsCreateOpen(false)} title="Register Webhook Destination">
        <form onSubmit={handleCreate} className="space-y-4">
          {createError && (
            <div className="p-3 rounded bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs">{createError}</div>
          )}
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Webhook Name *</label>
            <input type="text" required placeholder="e.g. Production Order Handler" value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 focus:outline-none focus:border-cyan-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Destination URL (HTTPS) *</label>
            <input type="url" required placeholder="https://client-api.example.com/webhooks" value={formData.destinationUrl}
              onChange={(e) => setFormData({ ...formData, destinationUrl: e.target.value })}
              className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-cyan-500" />
            <p className="text-xs text-slate-400 mt-1">Must be HTTPS. Localhost and private networks are blocked.</p>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Subscribed Events *</label>
            <input type="text" required value={formData.subscribedEvents}
              onChange={(e) => setFormData({ ...formData, subscribedEvents: e.target.value })}
              className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-sm text-slate-200 font-mono focus:outline-none focus:border-cyan-500" />
            <p className="text-xs text-slate-400 mt-1">Comma-separated event codes or '*' for all.</p>
          </div>
          <div className="flex justify-end gap-3 pt-3 border-t border-slate-700/60">
            <Button variant="ghost" type="button" onClick={() => setIsCreateOpen(false)}>Cancel</Button>
            <Button variant="primary" type="submit" isLoading={isSubmitting}>
              {isSubmitting ? 'Registering...' : 'Register Webhook'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Secret Revelation Modal */}
      <Modal isOpen={!!revealedSecret} onClose={() => setRevealedSecret(null)} title="Webhook Signing Secret">
        <div className="space-y-4">
          <div className="p-3 rounded bg-amber-500/10 border border-amber-500/30 flex items-start gap-3">
            <ShieldAlert className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
            <div className="text-xs text-amber-300">
              <p className="font-semibold mb-1">Save this HMAC secret now!</p>
              <p>This secret signs all webhook payloads for <span className="font-mono font-medium">{revealedSecret?.name}</span>. It is never stored in plain text and cannot be retrieved again.</p>
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">HMAC-SHA256 Signing Secret</label>
            <div className="flex items-center gap-2">
              <input type="text" readOnly value={revealedSecret?.secret || ''}
                className="w-full bg-slate-900 border border-slate-700 rounded px-3 py-2 text-xs font-mono text-cyan-300 select-all" />
              <Button variant="primary" size="sm" onClick={() => revealedSecret?.secret && copyToClipboard(revealedSecret.secret)}>
                {hasCopiedSecret ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                {hasCopiedSecret ? 'Copied' : 'Copy'}
              </Button>
            </div>
          </div>
          <div className="flex justify-end pt-3 border-t border-slate-700/60">
            <Button variant="primary" onClick={() => setRevealedSecret(null)}>I Have Saved My Secret</Button>
          </div>
        </div>
      </Modal>

      {/* Rotate Secret Confirmation */}
      <Modal isOpen={!!rotateTarget} onClose={() => setRotateTarget(null)} title="Rotate Webhook Signing Secret">
        <div className="space-y-4">
          <p className="text-sm text-slate-300">
            Rotate the signing secret for <span className="font-semibold text-slate-100">{rotateTarget?.name}</span>?
          </p>
          <p className="text-xs text-amber-400">Payloads signed with the old secret will fail validation until you update your receiver.</p>
          <div className="flex justify-end gap-3 pt-3 border-t border-slate-700/60">
            <Button variant="ghost" onClick={() => setRotateTarget(null)}>Cancel</Button>
            <Button variant="primary" isLoading={isRotating} onClick={handleRotateSecret}>
              {isRotating ? 'Rotating...' : 'Rotate Secret'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation */}
      <Modal isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} title="Delete Webhook">
        <div className="space-y-4">
          <p className="text-sm text-slate-300">
            Remove webhook <span className="font-semibold text-slate-100">{deleteTarget?.name}</span>?
          </p>
          <div className="flex justify-end gap-3 pt-3 border-t border-slate-700/60">
            <Button variant="ghost" onClick={() => setDeleteTarget(null)}>Cancel</Button>
            <Button variant="danger" isLoading={isDeleting} onClick={handleDelete}>
              {isDeleting ? 'Deleting...' : 'Confirm Delete'}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Delivery History Modal */}
      <Modal isOpen={!!deliveryWebhook} onClose={() => setDeliveryWebhook(null)} title={`Delivery History — ${deliveryWebhook?.name ?? ''}`}>
        <div className="space-y-3">
          {isLoadingDeliveries ? (
            <div className="py-8 text-center text-slate-400">
              <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2 text-cyan-400" />
              <p className="text-xs">Loading delivery attempts...</p>
            </div>
          ) : deliveries.length === 0 ? (
            <p className="text-xs text-slate-400 text-center py-6">No delivery records found.</p>
          ) : (
            <div className="max-h-80 overflow-y-auto divide-y divide-slate-800 text-xs">
              {deliveries.map((del) => (
                <div key={del.id} className="py-2.5 space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-slate-300">{del.eventType}</span>
                    <div className="flex items-center gap-2">
                      <Badge variant={del.status === 'SUCCESS' ? 'accent' : 'error'}>
                        {del.status} ({del.httpStatus ?? 'N/A'})
                      </Badge>
                      {del.status !== 'SUCCESS' && canManage && (
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={redeliveringId === del.id}
                          onClick={() => handleRedeliver(del.id)}
                        >
                          {redeliveringId === del.id ? 'Retrying...' : 'Retry Delivery'}
                        </Button>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center justify-between text-slate-400">
                    <span>Key: {del.idempotencyKey ?? 'None'}</span>
                    <span>{del.deliveredAt ? new Date(del.deliveredAt).toLocaleTimeString() : 'Pending'}</span>
                  </div>
                  {del.errorMessage && (
                    <div className="text-rose-400 truncate" title={del.errorMessage}>Error: {del.errorMessage}</div>
                  )}
                </div>
              ))}
            </div>
          )}
          <div className="flex justify-end pt-3 border-t border-slate-700/60">
            <Button variant="ghost" onClick={() => setDeliveryWebhook(null)}>Close</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
