import { Calendar, User, Mail } from 'lucide-react'
import { Modal } from '../common/Modal'
import { Button } from '../common/Button'
import { Badge } from '../common/Badge'
import type { UserResponse } from '../../types/user'

export interface UserDetailModalProps {
  user: UserResponse | null
  isOpen: boolean
  onClose: () => void
  onChangeRole: (user: UserResponse) => void
  onToggleStatus: (user: UserResponse) => void
}

export function UserDetailModal({
  user,
  isOpen,
  onClose,
  onChangeRole,
  onToggleStatus,
}: UserDetailModalProps) {
  if (!user) return null

  const isActive = user.status === 'ACTIVE'

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`User Profile: ${user.username}`}
      description="Console user identity, assigned RBAC permissions, and lifecycle state"
      size="md"
      footer={
        <div className="flex w-full items-center justify-between">
          <Button variant="outline" size="sm" onClick={onClose}>
            Close
          </Button>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                onClose()
                onChangeRole(user)
              }}
            >
              Change Role
            </Button>
            <Button
              variant={isActive ? 'outline' : 'primary'}
              size="sm"
              onClick={() => {
                onClose()
                onToggleStatus(user)
              }}
              className={
                isActive
                  ? 'border-rose-300 text-rose-700 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-300'
                  : 'bg-emerald-600 hover:bg-emerald-700 text-white'
              }
            >
              {isActive ? 'Deactivate' : 'Activate'}
            </Button>
          </div>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Profile Card */}
        <div className="grid grid-cols-2 gap-3 rounded-lg border border-slate-200 bg-slate-50/50 p-3 text-xs dark:border-slate-800 dark:bg-slate-900/50">
          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">User ID</span>
            <span className="font-mono font-medium text-slate-900 dark:text-slate-100">
              #{user.id}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Account Status</span>
            <div className="mt-0.5">
              <Badge variant={isActive ? 'success' : 'error'} dot>
                {user.status}
              </Badge>
            </div>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Username</span>
            <span className="font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-1">
              <User className="h-3 w-3 text-slate-400" />
              {user.username}
            </span>
          </div>

          <div>
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Assigned Role</span>
            <div className="mt-0.5">
              <Badge variant="accent">
                {user.role}
              </Badge>
            </div>
          </div>

          <div className="col-span-2">
            <span className="text-slate-500 dark:text-slate-400 block text-[11px]">Email Address</span>
            <span className="text-slate-800 dark:text-slate-200 flex items-center gap-1 break-all">
              <Mail className="h-3 w-3 text-slate-400 shrink-0" />
              {user.email}
            </span>
          </div>
        </div>

        {/* Timestamps */}
        <div className="space-y-1 text-xs text-slate-500 dark:text-slate-400 border-t border-slate-100 pt-3 dark:border-slate-800">
          <div className="flex items-center gap-1.5">
            <Calendar className="h-3.5 w-3.5" />
            <span>Account Provisioned: {new Date(user.createdAt).toLocaleString()}</span>
          </div>
          {user.updatedAt && (
            <div className="flex items-center gap-1.5">
              <Calendar className="h-3.5 w-3.5" />
              <span>Last Modified: {new Date(user.updatedAt).toLocaleString()}</span>
            </div>
          )}
        </div>
      </div>
    </Modal>
  )
}
