import { Spinner } from './Spinner'
import { cn } from '../../utils/cn'

export interface LoadingStateProps {
  message?: string
  className?: string
  size?: 'sm' | 'md' | 'lg'
}

export function LoadingState({
  message = 'Loading data...',
  className,
  size = 'md',
}: LoadingStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center p-8 text-center space-y-3',
        className
      )}
    >
      <Spinner size={size} />
      {message && <p className="text-xs font-medium text-slate-500">{message}</p>}
    </div>
  )
}

