import { AlertTriangle } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { EmptyState } from '../components/common/EmptyState'
import { Badge } from '../components/common/Badge'
import { PageHeader } from '../components/common/PageHeader'

export function DeadLetterPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Retry & Dead Letter Queue (DLQ)"
        description="Inspect exhausted poison events and trigger operator-initiated reprocessing."
        breadcrumbs={[{ label: 'Dead Letter Queue' }]}
        badge={<Badge variant="warning">OPERATOR / ADMIN</Badge>}
      />

      <Card>
        <CardHeader>
          <CardTitle>Dead-Letter Recovery Pipeline</CardTitle>
          <CardDescription>
            Secured endpoints: <code>GET /api/v1/admin/dead-letters</code> and{' '}
            <code>POST /api/v1/admin/dead-letters/{'{key}'}/reprocess</code>.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <EmptyState
            icon={<AlertTriangle className="h-5 w-5 text-amber-600 dark:text-amber-400" />}
            title="No Poison Events in Dead-Letter Queue"
            description="The dead-letter queue is currently clean. Any events failing after 3 exponential backoff attempts will be captured here."
          />
        </CardContent>
      </Card>
    </div>
  )
}

