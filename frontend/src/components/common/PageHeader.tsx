import React from 'react'
import { Breadcrumbs, type BreadcrumbItem } from './Breadcrumbs'
import { cn } from '../../utils/cn'

export interface PageHeaderProps {
  title: string
  description?: string
  breadcrumbs?: BreadcrumbItem[]
  badge?: React.ReactNode
  actions?: React.ReactNode
  className?: string
}

export function PageHeader({
  title,
  description,
  breadcrumbs,
  badge,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <div
      className={cn(
        'flex flex-col gap-3 border-b border-slate-200 pb-5 dark:border-slate-800',
        className
      )}
    >
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Breadcrumbs items={breadcrumbs} className="mb-0.5" />
      )}

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="space-y-0.5">
          <div className="flex items-center gap-2.5">
            <h1 className="text-xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              {title}
            </h1>
            {badge && <div className="shrink-0">{badge}</div>}
          </div>
          {description && (
            <p className="text-xs text-slate-500 dark:text-slate-400 max-w-2xl">
              {description}
            </p>
          )}
        </div>

        {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
      </div>
    </div>
  )
}

