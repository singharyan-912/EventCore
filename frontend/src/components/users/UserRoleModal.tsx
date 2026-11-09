import { useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import { Modal } from '../common/Modal'
import { Button } from '../common/Button'
import { Spinner } from '../common/Spinner'
import type { Role } from '../../types/auth'
import type { UserResponse } from '../../types/user'

export interface UserRoleModalProps {
  user: UserResponse | null
  isOpen: boolean
  onClose: () => void
  onUpdateRole: (id: number, newRole: Role) => Promise<unknown>
}

export function UserRoleModal({ user, isOpen, onClose, onUpdateRole }: UserRoleModalProps) {
  const [selectedRole, setSelectedRole] = useState<Role>(user?.role || 'OPERATOR')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (!user) return null

  const roles: { role: Role; description: string }[] = [
    {
      role: 'ADMIN',
      description: 'Full access: User management, audit logs, DLQ recovery, and platform operations.',
    },
    {
      role: 'OPERATOR',
      description: 'Operations access: DLQ inspection and reprocessing, event processing, and metrics.',
    },
    {
      role: 'VIEWER',
      description: 'Read-only access: Inspect events, outbox status, and monitoring telemetry.',
    },
  ]

  const handleSubmit = async () => {
    setErrorMessage(null)
    setIsSubmitting(true)
    try {
      await onUpdateRole(user.id, selectedRole)
      onClose()
    } catch (err: unknown) {
      const msg =
        (err as { message?: string })?.message ||
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to update user role.'
      setErrorMessage(msg)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={isSubmitting ? () => {} : onClose}
      title={`Update Role for ${user.username}`}
      description="Modify user RBAC role and system authorization scope."
      size="md"
      footer={
        <div className="flex w-full items-center justify-between">
          <Button variant="outline" size="sm" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleSubmit}
            disabled={isSubmitting || selectedRole === user.role}
          >
            {isSubmitting ? (
              <>
                <Spinner size="sm" className="mr-1.5" />
                Updating...
              </>
            ) : (
              'Save Role'
            )}
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {errorMessage && (
          <div className="flex items-start gap-2.5 rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
            <AlertTriangle className="h-4 w-4 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
            <p>{errorMessage}</p>
          </div>
        )}

        {user.role === 'ADMIN' && (
          <div className="flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50/70 p-3 text-xs text-amber-900 dark:border-amber-900/40 dark:bg-amber-950/30 dark:text-amber-200">
            <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
            <p className="leading-relaxed">
              <strong>Last-Admin Lockout Protection:</strong> The backend prevents demoting the last active administrator. If this user is the only active admin, this change will be rejected.
            </p>
          </div>
        )}

        <div className="space-y-2">
          {roles.map((r) => (
            <label
              key={r.role}
              className={`flex items-start gap-3 rounded-lg border p-3 cursor-pointer transition-colors ${
                selectedRole === r.role
                  ? 'border-indigo-600 bg-indigo-50/70 dark:border-indigo-500 dark:bg-indigo-950/40'
                  : 'border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-800/50'
              }`}
            >
              <input
                type="radio"
                name="user-role"
                value={r.role}
                checked={selectedRole === r.role}
                onChange={() => setSelectedRole(r.role)}
                className="mt-1 text-indigo-600 focus:ring-indigo-500"
              />
              <div className="text-xs">
                <span className="font-semibold text-slate-900 dark:text-slate-100 block">
                  {r.role}
                </span>
                <span className="text-slate-500 dark:text-slate-400 text-[11px] leading-relaxed">
                  {r.description}
                </span>
              </div>
            </label>
          ))}
        </div>
      </div>
    </Modal>
  )
}
