import { useState } from 'react'
import {
  KeyRound,
  Plus,
  RefreshCw,
  Copy,
  Check,
  RotateCw,
  Ban,
  AlertTriangle,
  ShieldCheck,
  Calendar,
} from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Modal } from '../components/common/Modal'
import { EmptyState } from '../components/common/EmptyState'
import { useApiKeys } from '../hooks/useApiKeys'
import { tokenStorage } from '../utils/tokenStorage'
import type { ApiKeyResponse, CreateApiKeyResponse } from '../types/apiKey'
import type { Role } from '../types/auth'

export function ApiKeysPage() {
  const user = tokenStorage.getUser()
  const userRole = (user?.role as Role) || 'VIEWER'
  const canManageKeys = userRole === 'ADMIN' || userRole === 'OPERATOR'

  const {
    keys,
    isLoading,
    isFetching,
    error,
    createKey,
    isCreating,
    revokeKey,
    isRevoking,
    rotateKey,
    isRotating,
    refetch,
  } = useApiKeys()

  // Creation modal state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [keyName, setKeyName] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  // One-time secret display state
  const [secretResult, setSecretResult] = useState<CreateApiKeyResponse | null>(null)
  const [hasCopied, setHasCopied] = useState(false)

  // Revoke confirmation modal state
  const [keyToRevoke, setKeyToRevoke] = useState<ApiKeyResponse | null>(null)

  // Rotate confirmation modal state
  const [keyToRotate, setKeyToRotate] = useState<ApiKeyResponse | null>(null)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!keyName.trim()) return

    setFormError(null)

    try {
      const result = await createKey({
        name: keyName.trim(),
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      })
      setIsCreateModalOpen(false)
      setKeyName('')
      setExpiresAt('')
      setSecretResult(result)
    } catch (err: unknown) {
      const e = err as { message?: string }
      setFormError(e?.message || 'Failed to create API key.')
    }
  }

  const handleRevoke = async () => {
    if (!keyToRevoke) return
    try {
      await revokeKey(keyToRevoke.id)
      setKeyToRevoke(null)
    } catch {
      // Error is tracked by mutation
    }
  }

  const handleRotate = async () => {
    if (!keyToRotate) return
    try {
      const result = await rotateKey(keyToRotate.id)
      setKeyToRotate(null)
      setSecretResult(result)
    } catch {
      // Error is tracked by mutation
    }
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    setHasCopied(true)
    setTimeout(() => setHasCopied(false), 2000)
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'ACTIVE':
        return <Badge variant="success" dot>ACTIVE</Badge>
      case 'REVOKED':
        return <Badge variant="error" dot>REVOKED</Badge>
      case 'EXPIRED':
        return <Badge variant="warning" dot>EXPIRED</Badge>
      default:
        return <Badge variant="neutral">{status}</Badge>
    }
  }

  const formatDate = (isoString: string | null) => {
    if (!isoString) return 'Never'
    try {
      const d = new Date(isoString)
      return d.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch {
      return isoString
    }
  }

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <PageHeader
        title="API Credentials & Keys"
        description="Manage tenant-scoped API keys for programmatic event ingestion and query access. Raw secrets are securely hashed with SHA-256."
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => refetch()}
              disabled={isLoading || isFetching}
              className="flex items-center gap-1.5"
            >
              <RefreshCw className={`h-4 w-4 ${isFetching ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
            {canManageKeys && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => setIsCreateModalOpen(true)}
                className="flex items-center gap-1.5"
              >
                <Plus className="h-4 w-4" />
                Generate Key
              </Button>
            )}
          </div>
        }
      />

      {/* Error alert */}
      {error && (
        <div className="flex items-center gap-2 rounded-lg bg-rose-50 border border-rose-200 p-4 text-xs text-rose-700 dark:bg-rose-950/60 dark:border-rose-900 dark:text-rose-300">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          <span>{error.message || 'Failed to load API keys.'}</span>
        </div>
      )}

      {/* Info Card explaining API authentication */}
      <Card className="border-indigo-100 bg-indigo-50/40 dark:border-indigo-900/40 dark:bg-indigo-950/20">
        <CardContent className="py-3 px-4 flex items-center justify-between text-xs text-indigo-950 dark:text-indigo-200">
          <div className="flex items-center gap-2.5">
            <ShieldCheck className="h-5 w-5 text-indigo-600 dark:text-indigo-400 shrink-0" />
            <div>
              <span className="font-semibold block">Using your API Key</span>
              <span>
                Pass your active key in the HTTP request header: <code className="font-mono bg-white/80 dark:bg-slate-900 px-1.5 py-0.5 rounded border border-indigo-200 dark:border-indigo-800">X-API-Key: rf_live_...</code>. API keys inherit <code className="font-mono">OPERATOR</code> permissions scoped exclusively to your tenant.
              </span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* API Keys Table */}
      <Card>
        <CardHeader className="border-b border-slate-100 dark:border-slate-800">
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="text-base">Issued Keys</CardTitle>
              <CardDescription>
                {keys.length} credential{keys.length === 1 ? '' : 's'} registered under current tenant
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="flex items-center justify-center p-12 text-slate-400">
              <RefreshCw className="h-6 w-6 animate-spin mr-2" />
              <span className="text-sm">Loading API credentials...</span>
            </div>
          ) : keys.length === 0 ? (
            <div className="p-8">
              <EmptyState
                icon={<KeyRound className="h-5 w-5" />}
                title="No API keys provisioned"
                description="Generate an API key to enable secure background ingestion and external service integration."
                actionLabel={canManageKeys ? 'Generate API Key' : undefined}
                onAction={canManageKeys ? () => setIsCreateModalOpen(true) : undefined}
              />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs text-slate-600 dark:text-slate-400">
                <thead className="bg-slate-50 text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:bg-slate-800/60 dark:text-slate-400 border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th className="py-3 px-4">Name</th>
                    <th className="py-3 px-4">Prefix</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Created</th>
                    <th className="py-3 px-4">Last Used</th>
                    <th className="py-3 px-4">Expires</th>
                    {canManageKeys && <th className="py-3 px-4 text-right">Actions</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                  {keys.map((k) => (
                    <tr
                      key={k.id}
                      className="hover:bg-slate-50/60 transition-colors dark:hover:bg-slate-800/40"
                    >
                      <td className="py-3.5 px-4 font-semibold text-slate-900 dark:text-slate-100">
                        {k.name}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-slate-700 dark:text-slate-300">
                        <code>{k.keyPrefix}...</code>
                      </td>
                      <td className="py-3.5 px-4">
                        {getStatusBadge(k.status)}
                      </td>
                      <td className="py-3.5 px-4 text-slate-500">
                        {formatDate(k.createdAt)}
                      </td>
                      <td className="py-3.5 px-4 text-slate-500">
                        {formatDate(k.lastUsedAt)}
                      </td>
                      <td className="py-3.5 px-4 text-slate-500">
                        {k.expiresAt ? formatDate(k.expiresAt) : 'Never'}
                      </td>
                      {canManageKeys && (
                        <td className="py-3.5 px-4 text-right space-x-1 whitespace-nowrap">
                          {k.status === 'ACTIVE' && (
                            <>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setKeyToRotate(k)}
                                title="Rotate API Key"
                                disabled={isRotating}
                                className="h-7 px-2 text-slate-700 dark:text-slate-300"
                              >
                                <RotateCw className="h-3.5 w-3.5 mr-1 text-slate-500" />
                                Rotate
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setKeyToRevoke(k)}
                                title="Revoke API Key"
                                disabled={isRevoking}
                                className="h-7 px-2 text-rose-600 border-rose-200 hover:bg-rose-50 dark:border-rose-900 dark:text-rose-400 dark:hover:bg-rose-950/40"
                              >
                                <Ban className="h-3.5 w-3.5 mr-1" />
                                Revoke
                              </Button>
                            </>
                          )}
                          {k.status === 'REVOKED' && (
                            <span className="text-[11px] text-slate-400 italic">Revoked</span>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Modal: Create Key */}
      <Modal
        isOpen={isCreateModalOpen}
        onClose={() => !isCreating && setIsCreateModalOpen(false)}
        title="Generate New API Key"
        description="Creates a cryptographically secure key for programmatic access to this tenant."
        size="md"
        footer={
          <div className="flex justify-end gap-2 w-full">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsCreateModalOpen(false)}
              disabled={isCreating}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={handleCreate}
              isLoading={isCreating}
              disabled={!keyName.trim()}
            >
              Generate Key
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreate} className="space-y-4 py-2">
          {formError && (
            <div className="flex items-center gap-2 rounded-md bg-rose-50 border border-rose-200 p-2.5 text-xs text-rose-700 dark:bg-rose-950/60 dark:border-rose-900 dark:text-rose-300">
              <AlertTriangle className="h-4 w-4 shrink-0" />
              <span>{formError}</span>
            </div>
          )}

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
              Key Name / Purpose <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={keyName}
              onChange={(e) => setKeyName(e.target.value)}
              placeholder="e.g. Ingestion Pipeline Worker"
              className="w-full rounded-md border border-slate-300 bg-white py-2 px-3 text-xs text-slate-900 placeholder:text-slate-400 focus:border-indigo-600 focus:outline-none focus:ring-1 focus:ring-indigo-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
              required
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
              Expiration Date <span className="text-slate-400 font-normal">(Optional)</span>
            </label>
            <div className="relative">
              <Calendar className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <input
                type="datetime-local"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
                className="w-full rounded-md border border-slate-300 bg-white py-2 pl-9 pr-3 text-xs text-slate-900 focus:border-indigo-600 focus:outline-none focus:ring-1 focus:ring-indigo-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
              />
            </div>
            <p className="text-[11px] text-slate-400">
              Leave blank if the key should remain active indefinitely until manually revoked.
            </p>
          </div>
        </form>
      </Modal>

      {/* Modal: One-Time Secret Display */}
      {secretResult && (
        <Modal
          isOpen={true}
          onClose={() => setSecretResult(null)}
          title="Save Your Secret API Key"
          description="Copy your key now. For your security, this key will never be shown again."
          size="md"
          footer={
            <div className="flex justify-end w-full">
              <Button
                variant="primary"
                size="sm"
                onClick={() => setSecretResult(null)}
              >
                Done
              </Button>
            </div>
          }
        >
          <div className="space-y-4 py-2">
            <div className="flex items-start gap-2.5 rounded-md bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800 dark:bg-amber-950/50 dark:border-amber-900 dark:text-amber-300">
              <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" />
              <span>
                Please store this key securely in your secret manager or environment configuration. If you lose it, you will need to rotate or generate a new key.
              </span>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                Secret API Key ({secretResult.name})
              </label>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  readOnly
                  value={secretResult.secretKey}
                  className="w-full font-mono rounded-md border border-slate-300 bg-slate-50 py-2 px-3 text-xs text-slate-900 select-all dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                />
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => copyToClipboard(secretResult.secretKey)}
                  className="shrink-0 flex items-center gap-1.5"
                >
                  {hasCopied ? (
                    <>
                      <Check className="h-4 w-4 text-emerald-600" />
                      <span className="text-emerald-600">Copied</span>
                    </>
                  ) : (
                    <>
                      <Copy className="h-4 w-4" />
                      <span>Copy</span>
                    </>
                  )}
                </Button>
              </div>
            </div>
          </div>
        </Modal>
      )}

      {/* Modal: Confirm Revocation */}
      {keyToRevoke && (
        <Modal
          isOpen={true}
          onClose={() => !isRevoking && setKeyToRevoke(null)}
          title="Revoke API Key"
          size="sm"
          footer={
            <div className="flex justify-end gap-2 w-full">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setKeyToRevoke(null)}
                disabled={isRevoking}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={handleRevoke}
                isLoading={isRevoking}
              >
                Revoke Immediately
              </Button>
            </div>
          }
        >
          <div className="space-y-3 py-2 text-xs text-slate-600 dark:text-slate-400">
            <p>
              Are you sure you want to revoke <strong className="text-slate-900 dark:text-slate-100">{keyToRevoke.name}</strong> (<code className="font-mono">{keyToRevoke.keyPrefix}...</code>)?
            </p>
            <div className="rounded-md bg-rose-50 border border-rose-200 p-2.5 text-rose-800 dark:bg-rose-950/40 dark:border-rose-900 dark:text-rose-300">
              Any external services, workers, or scripts currently using this key will immediately be rejected with <strong>401 Unauthorized</strong>.
            </div>
          </div>
        </Modal>
      )}

      {/* Modal: Confirm Rotation */}
      {keyToRotate && (
        <Modal
          isOpen={true}
          onClose={() => !isRotating && setKeyToRotate(null)}
          title="Rotate API Key"
          size="sm"
          footer={
            <div className="flex justify-end gap-2 w-full">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setKeyToRotate(null)}
                disabled={isRotating}
              >
                Cancel
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={handleRotate}
                isLoading={isRotating}
              >
                Rotate and Issue New Key
              </Button>
            </div>
          }
        >
          <div className="space-y-3 py-2 text-xs text-slate-600 dark:text-slate-400">
            <p>
              Rotating <strong className="text-slate-900 dark:text-slate-100">{keyToRotate.name}</strong> will revoke the existing key and generate a new secret.
            </p>
            <div className="rounded-md bg-amber-50 border border-amber-200 p-2.5 text-amber-800 dark:bg-amber-950/40 dark:border-amber-900 dark:text-amber-300">
              The existing key (<code className="font-mono">{keyToRotate.keyPrefix}...</code>) will be permanently deactivated immediately upon rotation.
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
