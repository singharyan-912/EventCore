import { useState } from 'react'
import { AlertTriangle, Power, CheckCircle2 } from 'lucide-react'
import { Modal } from '../common/Modal'
import { Button } from '../common/Button'
import { Spinner } from '../common/Spinner'
import type { UserStatus } from '../../types/auth'
import type { UserResponse } from '../../types/user'

export interface UserStatusModalProps {
  user: UserResponse | null
  isOpen: boolean
  onClose: () => void
  onUpdateStatus: (id: number, newStatus: UserStatus) => Promise<unknown>
}

export function UserStatusModal({ user, isOpen, onClose, onUpdateStatus }: UserStatusModalProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (!user) return null

  const isCurrentlyActive = user.status === 'ACTIVE'
  const newStatus: UserStatus = isCurrentlyActive ? 'DEACTIVATED' : 'ACTIVE'

  const handleSubmit = async () => {
    setErrorMessage(null)
    setIsSubmitting(true)
    try {
      await onUpdateStatus(user.id, newStatus)
      onClose()
    } catch (err: unknown) {
      const msg =
        (err as { message?: string })?.message ||
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        `Failed to ${isCurrentlyActive ? 'deactivate' : 'activate'} user.`
      setErrorMessage(msg)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={isSubmitting ? () => {} : onClose}
      title={`${isCurrentlyActive ? 'Deactivate' : 'Activate'} Account: ${user.username}`}
      description={`Change account lifecycle status for ${user.username} (${user.email})`}
      size="md"
      footer={
        <div className="flex w-full items-center justify-between">
          <Button variant="outline" size="sm" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant={isCurrentlyActive ? 'primary' : 'primary'}
            size="sm"
            onClick={handleSubmit}
            disabled={isSubmitting}
            className={
              isCurrentlyActive
                ? 'bg-rose-600 hover:bg-rose-700 text-white'
                : 'bg-emerald-600 hover:bg-emerald-700 text-white'
            }
          >
            {isSubmitting ? (
              <>
                <Spinner size="sm" className="mr-1.5" />
                Processing...
              </>
            ) : isCurrentlyActive ? (
              <>
                <Power className="mr-1.5 h-3.5 w-3.5" />
                Confirm Deactivation
              </>
            ) : (
              <>
                <CheckCircle2 className="mr-1.5 h-3.5 w-3.5" />
                Confirm Activation
              </>
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

        {isCurrentlyActive ? (
          <div className="rounded-lg border border-rose-200 bg-rose-50/70 p-3 text-xs text-rose-900 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300 space-y-1.5">
            <div className="flex items-center gap-1.5 font-semibold">
              <AlertTriangle className="h-4 w-4 text-rose-600 dark:text-rose-400 shrink-0" />
              Account Deactivation Notice
            </div>
            <p className="leading-relaxed">
              Deactivating <strong>{user.username}</strong> will immediately invalidate their active authentication session. The user will be unable to sign in, refresh tokens, or execute operational commands.
            </p>
          </div>
        ) : (
          <div className="rounded-lg border border-emerald-200 bg-emerald-50/70 p-3 text-xs text-emerald-900 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300 space-y-1.5">
            <div className="flex items-center gap-1.5 font-semibold">
              <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
              Account Activation Notice
            </div>
            <p className="leading-relaxed">
              Activating <strong>{user.username}</strong> restores their access according to their assigned <strong>{user.role}</strong> role.
            </p>
          </div>
        )}

        {user.role === 'ADMIN' && isCurrentlyActive && (
          <div className="flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50/70 p-3 text-xs text-amber-900 dark:border-amber-900/40 dark:bg-amber-950/30 dark:text-amber-200">
            <AlertTriangle className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
            <p className="leading-relaxed">
              <strong>Last-Admin Lockout Protection:</strong> The backend prevents deactivating the last active administrator. If this user is the only active admin, deactivation will be rejected.
            </p>
          </div>
        )}

        <div className="text-xs text-slate-500 dark:text-slate-400">
          Target user: <span className="font-semibold text-slate-800 dark:text-slate-200">{user.username}</span> ({user.email})
        </div>
      </div>
    </Modal>
  )
}
