import { Activity } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { EmptyState } from '../components/common/EmptyState'
import { PageHeader } from '../components/common/PageHeader'

export function MonitoringPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="System Monitoring & Observability"
        description="Micrometer telemetry, Spring Boot Actuator health indicators, and JVM performance."
        breadcrumbs={[{ label: 'System Monitoring' }]}
      />

      <Card>
        <CardHeader>
          <CardTitle>Spring Boot Actuator Telemetry</CardTitle>
          <CardDescription>
            Exposed at <code>/actuator/health</code>, <code>/actuator/metrics</code>, and{' '}
            <code>GET /api/v1/operations/metrics</code>.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <EmptyState
            icon={<Activity className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />}
            title="Monitoring Telemetry Ready"
            description="Inspect health indicators (db, kafka, outbox, retryWorker) and bounded Micrometer meters without JVM cardinality explosion."
          />
        </CardContent>
      </Card>
    </div>
  )
}

