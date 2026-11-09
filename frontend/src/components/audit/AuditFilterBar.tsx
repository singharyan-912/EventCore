import { Search, Filter, RotateCcw } from 'lucide-react'
import { Button } from '../common/Button'
import { Input } from '../common/Input'

export interface AuditFilterBarProps {
  actionType: string
  onActionTypeChange: (val: string) => void
  targetIdentifier: string
  onTargetIdentifierChange: (val: string) => void
  outcome: string
  onOutcomeChange: (val: string) => void
  onApply: () => void
  onReset: () => void
  isLoading?: boolean
}

export function AuditFilterBar({
  actionType,
  onActionTypeChange,
  targetIdentifier,
  onTargetIdentifierChange,
  outcome,
  onOutcomeChange,
  onApply,
  onReset,
  isLoading,
}: AuditFilterBarProps) {
  const outcomes = ['ALL', 'SUCCESS', 'FAILED']

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onApply()
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3.5 shadow-xs dark:border-slate-800 dark:bg-slate-900 space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Filter className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
          <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
            Audit Filters
          </span>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={onReset}
            className="text-xs h-8"
            disabled={isLoading}
          >
            <RotateCcw className="mr-1 h-3.5 w-3.5" />
            Reset
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={onApply}
            className="text-xs h-8"
            disabled={isLoading}
          >
            <Search className="mr-1 h-3.5 w-3.5" />
            Apply
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {/* Action Type */}
        <Input
          placeholder="Action type (e.g. USER_CREATE)..."
          value={actionType}
          onChange={(e) => onActionTypeChange(e.target.value)}
          onKeyDown={handleKeyDown}
        />

        {/* Target Identifier */}
        <Input
          placeholder="Target identifier (username, key)..."
          value={targetIdentifier}
          onChange={(e) => onTargetIdentifierChange(e.target.value)}
          onKeyDown={handleKeyDown}
        />

        {/* Outcome Selector */}
        <div className="flex items-center gap-1.5 pt-0.5">
          <span className="text-xs text-slate-500 dark:text-slate-400 mr-1">Outcome:</span>
          {outcomes.map((o) => (
            <button
              key={o}
              type="button"
              onClick={() => onOutcomeChange(o)}
              className={`rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors ${
                outcome === o
                  ? 'bg-indigo-600 text-white shadow-xs dark:bg-indigo-500'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700'
              }`}
            >
              {o}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
