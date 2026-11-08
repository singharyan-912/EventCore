import { ShieldAlert } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { EmptyState } from '../components/common/EmptyState'
import { Badge } from '../components/common/Badge'
import { PageHeader } from '../components/common/PageHeader'

export function AuditLogsPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Security & Operational Audit Logs"
        description="Immutable audit trail of authentication events, user role modifications, and recovery operations."
        breadcrumbs={[{ label: 'Audit Logs' }]}
        badge={<Badge variant="error">ADMIN ONLY</Badge>}
      />

      <Card>
        <CardHeader>
          <CardTitle>Audit Records Roster</CardTitle>
          <CardDescription>
            Secured endpoint: <code>GET /api/v1/operations/audit-logs</code> with authenticated actor derivation.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <EmptyState
            icon={<ShieldAlert className="h-5 w-5 text-rose-600 dark:text-rose-400" />}
            title="Audit Logging Enabled"
            description="All security lifecycle events (LOGIN_SUCCESS, USER_CREATED, DEAD_LETTER_REPROCESS, ACCESS_DENIED) are recorded in operational_audit_logs."
          />
        </CardContent>
      </Card>
    </div>
  )
}

