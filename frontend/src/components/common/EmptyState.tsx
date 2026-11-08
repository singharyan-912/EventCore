import React from 'react'
import { Inbox } from 'lucide-react'
import { cn } from '../../utils/cn'
import { Button } from './Button'

export interface EmptyStateProps {
  icon?: React.ReactNode
  title: string
  description?: string
  actionLabel?: string
  onAction?: () => void
  className?: string
}

export function EmptyState({
  icon,
  title,
  description,
  actionLabel,
  onAction,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center p-8 text-center rounded-lg border border-dashed border-slate-300 bg-slate-50/50 dark:border-slate-800 dark:bg-slate-900/40',
        className
      )}
    >
      <div className="flex h-11 w-11 items-center justify-center rounded-full bg-slate-100 text-slate-500 mb-3 border border-slate-200 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400">
        {icon || <Inbox className="h-5 w-5" />}
      </div>
      <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{title}</h4>
      {description && (
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400 max-w-sm">{description}</p>
      )}
      {actionLabel && onAction && (
        <div className="mt-4">
          <Button variant="outline" size="sm" onClick={onAction}>
            {actionLabel}
          </Button>
        </div>
      )}
    </div>
  )
}
