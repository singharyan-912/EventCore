import React from 'react'
import { cn } from '../../utils/cn'

export const Table = React.forwardRef<HTMLTableElement, React.HTMLAttributes<HTMLTableElement>>(
  ({ className, ...props }, ref) => (
    <div className="relative w-full overflow-auto rounded-md border border-slate-200 dark:border-slate-800">
      <table ref={ref} className={cn('w-full caption-bottom text-xs', className)} {...props} />
    </div>
  )
)
Table.displayName = 'Table'

export const TableHeader = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <thead
    ref={ref}
    className={cn(
      'border-b border-slate-200 bg-slate-50/80 font-semibold text-slate-700 dark:border-slate-800 dark:bg-slate-800/50 dark:text-slate-300',
      className
    )}
    {...props}
  />
))
TableHeader.displayName = 'TableHeader'

export const TableBody = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <tbody
    ref={ref}
    className={cn('divide-y divide-slate-100 bg-white dark:divide-slate-800 dark:bg-slate-900', className)}
    {...props}
  />
))
TableBody.displayName = 'TableBody'

export const TableRow = React.forwardRef<HTMLTableRowElement, React.HTMLAttributes<HTMLTableRowElement>>(
  ({ className, ...props }, ref) => (
    <tr
      ref={ref}
      className={cn(
        'transition-colors hover:bg-slate-50/70 dark:hover:bg-slate-800/40',
        className
      )}
      {...props}
    />
  )
)
TableRow.displayName = 'TableRow'

export const TableHead = React.forwardRef<
  HTMLTableCellElement,
  React.ThHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <th
    ref={ref}
    className={cn(
      'h-9 px-3.5 text-left align-middle font-semibold text-slate-600 dark:text-slate-400 select-none text-[11px] uppercase tracking-wider',
      className
    )}
    {...props}
  />
))
TableHead.displayName = 'TableHead'

export const TableCell = React.forwardRef<
  HTMLTableCellElement,
  React.TdHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <td
    ref={ref}
    className={cn('p-3.5 align-middle text-slate-800 dark:text-slate-200', className)}
    {...props}
  />
))
TableCell.displayName = 'TableCell'

