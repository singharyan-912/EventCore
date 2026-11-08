import { useContext } from 'react'
import { ThemeContext } from '../context/ThemeContext'
import type { Theme } from '../context/ThemeContext'

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider')
  }
  return context
}

export type { Theme }

