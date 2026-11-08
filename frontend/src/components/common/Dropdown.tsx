import React, { useState, useRef, useEffect } from 'react'
import { cn } from '../../utils/cn'

export interface DropdownItem {
  id: string
  label: string
  icon?: React.ReactNode
  onClick?: () => void
  danger?: boolean
  disabled?: boolean
  divider?: boolean
}

export interface DropdownProps {
  trigger: React.ReactNode
  items: DropdownItem[]
  align?: 'left' | 'right'
  className?: string
}

export function Dropdown({ trigger, items, align = 'right', className }: DropdownProps) {
  const [isOpen, setIsOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside)
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [isOpen])

  return (
    <div className={cn('relative inline-block text-left', className)} ref={menuRef}>
      <div onClick={() => setIsOpen((prev) => !prev)} className="cursor-pointer">
        {trigger}
      </div>

      {isOpen && (
        <div
          className={cn(
            'absolute z-50 mt-1.5 w-52 rounded-md border border-slate-200 bg-white py-1 text-xs shadow-lg ring-1 ring-black/5 dark:border-slate-800 dark:bg-slate-900',
            align === 'right' ? 'right-0' : 'left-0'
          )}
        >
          {items.map((item, index) => {
            if (item.divider) {
              return (
                <div
                  key={`div-${index}`}
                  className="my-1 border-t border-slate-100 dark:border-slate-800"
                />
              )
            }

            return (
              <button
                key={item.id}
                type="button"
                disabled={item.disabled}
                onClick={() => {
                  item.onClick?.()
                  setIsOpen(false)
                }}
                className={cn(
                  'flex w-full items-center gap-2 px-3 py-2 text-left transition-colors cursor-pointer disabled:cursor-not-allowed disabled:opacity-50',
                  item.danger
                    ? 'text-rose-600 hover:bg-rose-50 dark:text-rose-400 dark:hover:bg-rose-950/40'
                    : 'text-slate-700 hover:bg-slate-100 dark:text-slate-200 dark:hover:bg-slate-800'
                )}
              >
                {item.icon && <span className="shrink-0">{item.icon}</span>}
                <span className="truncate">{item.label}</span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

