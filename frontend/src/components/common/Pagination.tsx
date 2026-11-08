import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './Button'
import { cn } from '../../utils/cn'

export interface PaginationProps {
  page: number
  totalPages: number
  totalElements?: number
  size: number
  onPageChange: (newPage: number) => void
  onSizeChange?: (newSize: number) => void
  className?: string
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onSizeChange,
  className,
}: PaginationProps) {
  const isFirst = page === 0
  const isLast = totalPages === 0 || page >= totalPages - 1

  return (
    <div
      className={cn(
        'flex flex-col sm:flex-row items-center justify-between gap-3 px-2 py-3 text-xs text-slate-500 dark:text-slate-400',
        className
      )}
    >
      {/* Left: Total counts & page size */}
      <div className="flex items-center gap-3">
        {totalElements !== undefined && (
          <span>
            Total <strong className="font-semibold text-slate-700 dark:text-slate-200">{totalElements}</strong> items
          </span>
        )}
        {onSizeChange && (
          <div className="flex items-center gap-1.5">
            <span className="hidden sm:inline">Rows per page:</span>
            <select
              value={size}
              onChange={(e) => onSizeChange(Number(e.target.value))}
              className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700 shadow-2xs focus:border-indigo-600 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </div>
        )}
      </div>

      {/* Right: Page navigation */}
      <div className="flex items-center gap-2">
        <span className="font-medium text-slate-700 dark:text-slate-300">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="sm"
            onClick={() => onPageChange(page - 1)}
            disabled={isFirst}
            className="h-7 w-7 p-0"
            title="Previous page"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => onPageChange(page + 1)}
            disabled={isLast}
            className="h-7 w-7 p-0"
            title="Next page"
          >
            <ChevronRight className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>
    </div>
  )
}

