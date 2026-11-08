import React from 'react'
import { Filter, RotateCcw } from 'lucide-react'
import { Card, CardContent } from '../common/Card'
import { Button } from '../common/Button'
import type { EventFilterState } from '../../hooks/useEvents'

interface EventFilterBarProps {
  filters: EventFilterState
  onFilterChange: (filters: EventFilterState) => void
  onApply: () => void
  onReset: () => void
  isLoading?: boolean
}

export function EventFilterBar({
  filters,
  onFilterChange,
  onApply,
  onReset,
  isLoading,
}: EventFilterBarProps) {
  const handleChange = (field: keyof EventFilterState, value: string) => {
    onFilterChange({
      ...filters,
      [field]: value,
    })
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onApply()
    }
  }

  return (
    <Card>
      <CardContent className="p-4 space-y-3">
        <div className="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
          <div className="flex items-center gap-2">
            <Filter className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
            <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
              Query & Structured Filtering
            </span>
          </div>
          <span className="text-[10px] font-mono text-slate-400 dark:text-slate-500">
            Exact field matches backed by Spring Data Specification
          </span>
        </div>

        {/* Filter inputs grid */}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6" onKeyDown={handleKeyDown}>
          {/* 1. Status Dropdown */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-slate-600 dark:text-slate-400">Status</label>
            <select
              value={filters.status}
              onChange={(e) => handleChange('status', e.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-800 shadow-2xs focus:border-indigo-600 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            >
              <option value="">All Statuses</option>
              <option value="PROCESSED">PROCESSED</option>
              <option value="RETRY_PENDING">RETRY_PENDING</option>
              <option value="DEAD_LETTER">DEAD_LETTER</option>
              <option value="PROCESSING">PROCESSING</option>
            </select>
          </div>

          {/* 2. Event Type */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-slate-600 dark:text-slate-400">Event Type</label>
            <input
              type="text"
              placeholder="e.g. INVENTORY_DEDUCT"
              value={filters.eventType}
              onChange={(e) => handleChange('eventType', e.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-800 placeholder:text-slate-400 shadow-2xs focus:border-indigo-600 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:placeholder:text-slate-500"
            />
          </div>

          {/* 3. Resource ID */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-slate-600 dark:text-slate-400">Resource ID</label>
            <input
              type="text"
              placeholder="e.g. product-101"
              value={filters.resourceId}
              onChange={(e) => handleChange('resourceId', e.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-800 placeholder:text-slate-400 shadow-2xs focus:border-indigo-600 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:placeholder:text-slate-500"
            />
          </div>

          {/* 4. Idempotency Key */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-slate-600 dark:text-slate-400">Idempotency Key</label>
            <input
              type="text"
              placeholder="e.g. evt-992-abc"
              value={filters.idempotencyKey}
              onChange={(e) => handleChange('idempotencyKey', e.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-800 placeholder:text-slate-400 shadow-2xs focus:border-indigo-600 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:placeholder:text-slate-500"
            />
          </div>

          {/* 5. From Date */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-slate-600 dark:text-slate-400">From Date</label>
            <input
              type="date"
              value={filters.from}
              onChange={(e) => handleChange('from', e.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-800 shadow-2xs focus:border-indigo-600 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            />
          </div>

          {/* 6. To Date */}
          <div className="space-y-1">
            <label className="text-[11px] font-semibold text-slate-600 dark:text-slate-400">To Date</label>
            <input
              type="date"
              value={filters.to}
              onChange={(e) => handleChange('to', e.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-800 shadow-2xs focus:border-indigo-600 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            />
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex items-center justify-between pt-2 border-t border-slate-100 dark:border-slate-800">
          <span className="text-[11px] text-slate-500 dark:text-slate-400">
            Press <kbd className="rounded border border-slate-200 px-1 py-0.5 text-[10px] font-mono dark:border-slate-700">Enter</kbd> to apply search filters
          </span>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={onReset}
              disabled={isLoading}
            >
              <RotateCcw className="h-3 w-3 mr-1 text-slate-500" />
              Reset Filters
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={onApply}
              disabled={isLoading}
            >
              <Filter className="h-3 w-3 mr-1" />
              Apply Filters
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

