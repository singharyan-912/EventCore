import { Send, Clock, AlertTriangle, CheckCircle2, RotateCw } from 'lucide-react'
import { Card, CardContent } from '../common/Card'
import type { OutboxSummaryResponse } from '../../types/outbox'

export interface OutboxMetricsCardsProps {
  summary?: OutboxSummaryResponse
  isLoading?: boolean
}

export function OutboxMetricsCards({ summary, isLoading }: OutboxMetricsCardsProps) {
  const cards = [
    {
      title: 'Total Outbox Records',
      value: summary?.totalOutboxEvents ?? 0,
      icon: Send,
      color: 'text-indigo-600 dark:text-indigo-400',
      bgColor: 'bg-indigo-50 dark:bg-indigo-950/40',
      subtext: 'Durable ACID entries in PostgreSQL',
    },
    {
      title: 'Published to Kafka',
      value: summary?.publishedCount ?? 0,
      icon: CheckCircle2,
      color: 'text-emerald-600 dark:text-emerald-400',
      bgColor: 'bg-emerald-50 dark:bg-emerald-950/40',
      subtext: 'Delivered to message broker',
    },
    {
      title: 'Pending Publication',
      value: (summary?.pendingCount ?? 0) + (summary?.claimedCount ?? 0),
      icon: Clock,
      color: 'text-amber-600 dark:text-amber-400',
      bgColor: 'bg-amber-50 dark:bg-amber-950/40',
      subtext: `${summary?.claimedCount ?? 0} currently claimed by worker`,
    },
    {
      title: 'Publication Failures',
      value: summary?.failedCount ?? 0,
      icon: AlertTriangle,
      color: 'text-rose-600 dark:text-rose-400',
      bgColor: 'bg-rose-50 dark:bg-rose-950/40',
      subtext: 'Kafka connection or broker rejections',
    },
    {
      title: 'Retried Outbox Events',
      value: summary?.retriedCount ?? 0,
      icon: RotateCw,
      color: 'text-blue-600 dark:text-blue-400',
      bgColor: 'bg-blue-50 dark:bg-blue-950/40',
      subtext: 'Recovered via backoff retry loop',
    },
  ]

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3.5">
      {cards.map((card) => {
        const Icon = card.icon
        return (
          <Card key={card.title} className="relative overflow-hidden">
            <CardContent className="p-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-slate-500 dark:text-slate-400">
                  {card.title}
                </span>
                <div className={`rounded-md p-1.5 ${card.bgColor}`}>
                  <Icon className={`h-4 w-4 ${card.color}`} />
                </div>
              </div>
              <div className="mt-2 flex items-baseline">
                {isLoading ? (
                  <div className="h-7 w-16 animate-pulse rounded bg-slate-200 dark:bg-slate-800" />
                ) : (
                  <span className="font-mono text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
                    {card.value.toLocaleString()}
                  </span>
                )}
              </div>
              <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400 truncate">
                {card.subtext}
              </p>
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}

