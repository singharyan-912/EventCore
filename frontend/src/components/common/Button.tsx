import React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../utils/cn'

const buttonVariants = cva(
  'inline-flex items-center justify-center font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:pointer-events-none disabled:opacity-50 select-none cursor-pointer',
  {
    variants: {
      variant: {
        primary:
          'bg-indigo-600 text-white hover:bg-indigo-700 active:bg-indigo-800 shadow-xs dark:bg-indigo-500 dark:hover:bg-indigo-600',
        secondary:
          'bg-slate-100 text-slate-800 hover:bg-slate-200 active:bg-slate-300 border border-slate-200 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-700 dark:hover:bg-slate-700',
        outline:
          'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 active:bg-slate-100 shadow-xs dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800',
        ghost:
          'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100',
        danger:
          'bg-rose-600 text-white hover:bg-rose-700 active:bg-rose-800 shadow-xs dark:bg-rose-600 dark:hover:bg-rose-700',
      },
      size: {
        sm: 'h-8 px-3 text-xs rounded-md gap-1.5',
        md: 'h-9 px-4 text-sm rounded-md gap-2',
        lg: 'h-10 px-5 text-sm rounded-md gap-2.5',
        icon: 'h-9 w-9 p-0 rounded-md',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'md',
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  isLoading?: boolean
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, isLoading, children, disabled, ...props }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        className={cn(buttonVariants({ variant, size }), className)}
        {...props}
      >
        {isLoading && (
          <svg
            className="animate-spin -ml-1 mr-2 h-4 w-4 text-current"
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
          >
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            />
          </svg>
        )}
        {children}
      </button>
    )
  }
)

Button.displayName = 'Button'
