import { Users } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { EmptyState } from '../components/common/EmptyState'
import { Badge } from '../components/common/Badge'
import { PageHeader } from '../components/common/PageHeader'

export function UsersPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="User Management & Access Control"
        description="Provision console users, assign RBAC roles (ADMIN, OPERATOR, VIEWER), and manage account status."
        breadcrumbs={[{ label: 'User Management' }]}
        badge={<Badge variant="error">ADMIN ONLY</Badge>}
      />

      <Card>
        <CardHeader>
          <CardTitle>User Roster</CardTitle>
          <CardDescription>
            Secured endpoints: <code>/api/v1/admin/users/**</code> with last active admin lockout protection.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <EmptyState
            icon={<Users className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />}
            title="User Management Pipeline"
            description="Manage operators and viewers, change roles via PATCH /role, and toggle status via PATCH /status."
          />
        </CardContent>
      </Card>
    </div>
  )
}

