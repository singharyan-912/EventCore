import { Link } from 'react-router-dom'
import { ChevronRight, Home } from 'lucide-react'
import { cn } from '../../utils/cn'

export interface BreadcrumbItem {
  label: string
  to?: string
}

export interface BreadcrumbsProps {
  items: BreadcrumbItem[]
  className?: string
}

export function Breadcrumbs({ items, className }: BreadcrumbsProps) {
  return (
    <nav aria-label="Breadcrumb" className={cn('flex items-center text-xs text-slate-500 dark:text-slate-400', className)}>
      <ol className="flex items-center gap-1.5 flex-wrap">
        <li>
          <Link
            to="/"
            className="flex items-center text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 transition-colors"
            title="Dashboard Overview"
          >
            <Home className="h-3.5 w-3.5" />
          </Link>
        </li>
        {items.map((item, index) => {
          const isLast = index === items.length - 1
          return (
            <li key={index} className="flex items-center gap-1.5">
              <ChevronRight className="h-3 w-3 text-slate-300 dark:text-slate-600 shrink-0" />
              {isLast || !item.to ? (
                <span className="font-semibold text-slate-800 dark:text-slate-200 truncate max-w-[200px]">
                  {item.label}
                </span>
              ) : (
                <Link
                  to={item.to}
                  className="hover:text-slate-700 dark:hover:text-slate-200 transition-colors truncate max-w-[150px]"
                >
                  {item.label}
                </Link>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

