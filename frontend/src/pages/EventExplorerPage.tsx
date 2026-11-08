import { Layers, Search } from 'lucide-react'
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/common/Card'
import { EmptyState } from '../components/common/EmptyState'
import { PageHeader } from '../components/common/PageHeader'

export function EventExplorerPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Event Explorer"
        description="Query, filter, and inspect ingested events with correlated outbox status and idempotency hashes."
        breadcrumbs={[{ label: 'Event Explorer' }]}
        actions={
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" />
            <input
              type="text"
              placeholder="Filter by idempotency key..."
              className="h-8.5 w-64 rounded-md border border-slate-200 bg-white pl-8 pr-3 text-xs text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-slate-100 dark:placeholder:text-slate-500"
            />
          </div>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>Ingested Events Roster</CardTitle>
          <CardDescription>
            Backed by <code>GET /api/v1/operations/events</code> with multi-dimensional filtering.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <EmptyState
            icon={<Layers className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />}
            title="Event Explorer Ready"
            description="Connect to the backend via GET /api/v1/operations/events to view real-time ingested events and payload hashes."
          />
        </CardContent>
      </Card>
    </div>
  )
}

