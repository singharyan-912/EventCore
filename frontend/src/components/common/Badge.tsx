import React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../utils/cn'

const badgeVariants = cva(
  'inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium transition-colors focus:outline-none select-none font-mono tracking-tight',
  {
    variants: {
      variant: {
        success:
          'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300',
        warning:
          'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-300',
        error:
          'border-rose-200 bg-rose-50 text-rose-800 dark:border-rose-900/60 dark:bg-rose-950/40 dark:text-rose-300',
        info:
          'border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-900/60 dark:bg-blue-950/40 dark:text-blue-300',
        neutral:
          'border-slate-200 bg-slate-100 text-slate-700 dark:border-slate-800 dark:bg-slate-800/60 dark:text-slate-300',
        accent:
          'border-indigo-200 bg-indigo-50 text-indigo-800 dark:border-indigo-900/60 dark:bg-indigo-950/40 dark:text-indigo-300',
      },
    },
    defaultVariants: {
      variant: 'neutral',
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {
  dot?: boolean
}

export function Badge({ className, variant, dot, children, ...props }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ variant }), className)} {...props}>
      {dot && (
        <span
          className={cn('mr-1.5 h-1.5 w-1.5 rounded-full', {
            'bg-emerald-500': variant === 'success',
            'bg-amber-500': variant === 'warning',
            'bg-rose-500': variant === 'error',
            'bg-blue-500': variant === 'info',
            'bg-slate-500': variant === 'neutral',
            'bg-indigo-500': variant === 'accent',
          })}
        />
      )}
      {children}
    </span>
  )
}
