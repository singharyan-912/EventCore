import { Eye, Shield, Power, UserCheck, Users as UsersIcon } from 'lucide-react'
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '../common/Table'
import { Badge } from '../common/Badge'
import { Button } from '../common/Button'
import { Pagination } from '../common/Pagination'
import { EmptyState } from '../common/EmptyState'
import type { UserResponse } from '../../types/user'

export interface UserTableProps {
  users: UserResponse[]
  page: number
  totalPages: number
  totalElements: number
  pageSize: number
  onPageChange: (newPage: number) => void
  onInspect: (user: UserResponse) => void
  onChangeRole: (user: UserResponse) => void
  onToggleStatus: (user: UserResponse) => void
  isLoading?: boolean
}

export function UserTable({
  users,
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
  onInspect,
  onChangeRole,
  onToggleStatus,
  isLoading,
}: UserTableProps) {
  if (!isLoading && users.length === 0) {
    return (
      <EmptyState
        icon={<UsersIcon className="h-6 w-6 text-slate-400" />}
        title="No Users Registered"
        description="There are currently no users provisioned in the system. Use the 'Provision User' button above to create the first operator or viewer account."
      />
    )
  }

  const getRoleBadge = (role: string) => {
    switch (role) {
      case 'ADMIN':
        return <Badge variant="accent">ADMIN</Badge>
      case 'OPERATOR':
        return <Badge variant="warning">OPERATOR</Badge>
      default:
        return <Badge variant="neutral">VIEWER</Badge>
    }
  }

  return (
    <div className="space-y-3">
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-xs dark:border-slate-800 dark:bg-slate-900">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>User ID</TableHead>
              <TableHead>Username</TableHead>
              <TableHead>Email Address</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Account Status</TableHead>
              <TableHead>Provisioned At</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user) => {
              const isActive = user.status === 'ACTIVE'
              return (
                <TableRow key={user.id}>
                  {/* ID */}
                  <TableCell className="font-mono text-xs font-semibold text-slate-700 dark:text-slate-300">
                    #{user.id}
                  </TableCell>

                  {/* Username */}
                  <TableCell className="font-medium text-xs text-slate-900 dark:text-slate-100">
                    <button
                      type="button"
                      onClick={() => onInspect(user)}
                      className="hover:text-indigo-600 dark:hover:text-indigo-400 font-semibold"
                    >
                      {user.username}
                    </button>
                  </TableCell>

                  {/* Email */}
                  <TableCell className="text-xs text-slate-600 dark:text-slate-400">
                    {user.email}
                  </TableCell>

                  {/* Role */}
                  <TableCell>{getRoleBadge(user.role)}</TableCell>

                  {/* Status */}
                  <TableCell>
                    <Badge variant={isActive ? 'success' : 'error'} dot>
                      {user.status}
                    </Badge>
                  </TableCell>

                  {/* Created At */}
                  <TableCell className="text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">
                    {new Date(user.createdAt).toLocaleDateString([], {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </TableCell>

                  {/* Actions */}
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onInspect(user)}
                        className="h-7 px-2 text-xs"
                        title="View profile details"
                      >
                        <Eye className="mr-1 h-3.5 w-3.5" />
                        Inspect
                      </Button>

                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onChangeRole(user)}
                        className="h-7 px-2 text-xs"
                        title="Change user RBAC role"
                      >
                        <Shield className="mr-1 h-3 w-3 text-indigo-500" />
                        Role
                      </Button>

                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onToggleStatus(user)}
                        className={`h-7 px-2 text-xs ${
                          isActive
                            ? 'border-rose-200 text-rose-700 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-300 dark:hover:bg-rose-950/40'
                            : 'border-emerald-200 text-emerald-700 hover:bg-emerald-50 dark:border-emerald-800 dark:text-emerald-300 dark:hover:bg-emerald-950/40'
                        }`}
                        title={isActive ? 'Deactivate account' : 'Activate account'}
                      >
                        {isActive ? (
                          <>
                            <Power className="mr-1 h-3 w-3 text-rose-500" />
                            Deactivate
                          </>
                        ) : (
                          <>
                            <UserCheck className="mr-1 h-3 w-3 text-emerald-500" />
                            Activate
                          </>
                        )}
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      {totalPages > 1 && (
        <Pagination
          page={page}
          totalPages={totalPages}
          totalElements={totalElements}
          size={pageSize}
          onPageChange={onPageChange}
        />
      )}
    </div>
  )
}
