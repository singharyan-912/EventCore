import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Layers, Send } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { Button } from '../components/common/Button'
import { Badge } from '../components/common/Badge'
import { PageHeader } from '../components/common/PageHeader'

export function EventDetailsPage() {
  const { key } = useParams<{ key: string }>()
  const navigate = useNavigate()
  const displayKey = key || 'sample-evt-key'

  return (
    <div className="space-y-6">
      <PageHeader
        title={`Event: ${displayKey}`}
        description="Authoritative state from PostgreSQL processed_events and outbox_events tables."
        breadcrumbs={[
          { label: 'Event Explorer', to: '/events' },
          { label: displayKey },
        ]}
        actions={
          <Button variant="outline" size="sm" onClick={() => navigate('/events')}>
            <ArrowLeft className="h-3.5 w-3.5 mr-1" />
            Back to Explorer
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <Layers className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                Event Metadata
              </CardTitle>
              <Badge variant="success">PROCESSED</Badge>
            </div>
            <CardDescription>
              Backend endpoint: <code>GET /api/v1/operations/events/{'{idempotencyKey}'}</code>
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs">
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Idempotency Key</span>
              <span className="font-mono font-medium text-slate-900 dark:text-slate-100">{displayKey}</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Event Type</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">INVENTORY_DEDUCT</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Resource ID</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">product-101</span>
            </div>
            <div className="flex justify-between py-1.5">
              <span className="text-slate-500 dark:text-slate-400">Retry Count / Max</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">0 / 3</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <Send className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                Correlated Outbox Publication
              </CardTitle>
              <Badge variant="success">PUBLISHED</Badge>
            </div>
            <CardDescription>
              Transactional outbox state published to Apache Kafka.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-xs">
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Kafka Topic</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">inventory-events</span>
            </div>
            <div className="flex justify-between py-1.5 border-b border-slate-100 dark:border-slate-800">
              <span className="text-slate-500 dark:text-slate-400">Partition Key</span>
              <span className="font-mono text-slate-800 dark:text-slate-200">product-101</span>
            </div>
            <div className="flex justify-between py-1.5">
              <span className="text-slate-500 dark:text-slate-400">Delivery Guarantee</span>
              <span className="font-mono text-emerald-600 dark:text-emerald-400 font-medium">Effectively-Once</span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

