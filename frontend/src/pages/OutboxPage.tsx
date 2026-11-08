import { Send } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { EmptyState } from '../components/common/EmptyState'
import { PageHeader } from '../components/common/PageHeader'

export function OutboxPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Transactional Outbox Monitor"
        description="Monitor ACID outbox event publications, batch polling workers, and Apache Kafka producer status."
        breadcrumbs={[{ label: 'Outbox Monitor' }]}
      />

      <Card>
        <CardHeader>
          <CardTitle>Outbox Batch Monitor</CardTitle>
          <CardDescription>
            Powered by <code>GET /api/v1/operations/outbox/summary</code> and PostgreSQL SKIP LOCKED worker.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <EmptyState
            icon={<Send className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />}
            title="Transactional Outbox Ready"
            description="Outbox publisher polling every 2000ms with batch size 10 to ensure zero message loss across application restarts."
          />
        </CardContent>
      </Card>
    </div>
  )
}

