import { useState } from 'react'
import { RefreshCw, UserPlus, ShieldAlert } from 'lucide-react'
import { PageHeader } from '../components/common/PageHeader'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { AccessDenied } from '../components/common/AccessDenied'
import { UserTable } from '../components/users/UserTable'
import { UserDetailModal } from '../components/users/UserDetailModal'
import { CreateUserModal } from '../components/users/CreateUserModal'
import { UserRoleModal } from '../components/users/UserRoleModal'
import { UserStatusModal } from '../components/users/UserStatusModal'
import { useUsers } from '../hooks/useUsers'
import { tokenStorage } from '../utils/tokenStorage'
import type { UserResponse, CreateUserRequest } from '../types/user'
import type { Role, UserStatus } from '../types/auth'

export function UsersPage() {
  const user = tokenStorage.getUser()
  const isAdmin = user?.role === 'ADMIN'

  const {
    page,
    setPage,
    size,
    selectedUser,
    setSelectedUser,
    usersPage,
    isLoading,
    isFetching,
    error,
    createUser,
    updateRole,
    updateStatus,
    refreshAll,
  } = useUsers()

  // Modal states
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [roleModalUser, setRoleModalUser] = useState<UserResponse | null>(null)
  const [statusModalUser, setStatusModalUser] = useState<UserResponse | null>(null)

  // Non-admins see Access Restricted
  if (!isAdmin) {
    return (
      <AccessDenied
        requiredRole="ADMIN"
        featureName="User Management & RBAC Administration"
      />
    )
  }

  const isForbidden = (error as { status?: number })?.status === 403

  const handleCreate = async (request: CreateUserRequest) => {
    await createUser(request)
  }

  const handleUpdateRole = async (id: number, newRole: Role) => {
    await updateRole({ id, role: newRole })
  }

  const handleUpdateStatus = async (id: number, newStatus: UserStatus) => {
    await updateStatus({ id, status: newStatus })
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="User Management & Access Control"
        description="Provision console users, assign RBAC roles (ADMIN, OPERATOR, VIEWER), and manage account status."
        breadcrumbs={[{ label: 'User Management' }]}
        badge={<Badge variant="accent">ADMIN ACCESS</Badge>}
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="primary"
              size="sm"
              onClick={() => setCreateModalOpen(true)}
              className="text-xs"
            >
              <UserPlus className="mr-1.5 h-3.5 w-3.5" />
              Provision User
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={refreshAll}
              disabled={isFetching}
              className="text-xs"
            >
              <RefreshCw className={`mr-1.5 h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
              Refresh
            </Button>
          </div>
        }
      />

      {isForbidden && (
        <div className="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-xs text-rose-800 dark:border-rose-900/50 dark:bg-rose-950/40 dark:text-rose-300">
          <ShieldAlert className="h-5 w-5 text-rose-600 dark:text-rose-400 shrink-0 mt-0.5" />
          <div>
            <h4 className="font-semibold">Access Forbidden (403)</h4>
            <p className="mt-0.5">
              The backend returned 403 Forbidden for <code>/api/v1/admin/users</code>. Administrator permissions required.
            </p>
          </div>
        </div>
      )}

      {/* Main Table */}
      {!isForbidden && (
        <UserTable
          users={usersPage?.content ?? []}
          page={page}
          totalPages={usersPage?.totalPages ?? 0}
          totalElements={usersPage?.totalElements ?? 0}
          pageSize={size}
          onPageChange={setPage}
          onInspect={setSelectedUser}
          onChangeRole={setRoleModalUser}
          onToggleStatus={setStatusModalUser}
          isLoading={isLoading}
        />
      )}

      {/* Inspect Profile Modal */}
      <UserDetailModal
        user={selectedUser}
        isOpen={Boolean(selectedUser)}
        onClose={() => setSelectedUser(null)}
        onChangeRole={(u) => {
          setSelectedUser(null)
          setRoleModalUser(u)
        }}
        onToggleStatus={(u) => {
          setSelectedUser(null)
          setStatusModalUser(u)
        }}
      />

      {/* Provision User Modal */}
      <CreateUserModal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        onCreate={handleCreate}
      />

      {/* Change Role Modal */}
      <UserRoleModal
        user={roleModalUser}
        isOpen={Boolean(roleModalUser)}
        onClose={() => setRoleModalUser(null)}
        onUpdateRole={handleUpdateRole}
      />

      {/* Toggle Status Modal */}
      <UserStatusModal
        user={statusModalUser}
        isOpen={Boolean(statusModalUser)}
        onClose={() => setStatusModalUser(null)}
        onUpdateStatus={handleUpdateStatus}
      />
    </div>
  )
}
