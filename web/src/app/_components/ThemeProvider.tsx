'use client'

import type { ReactNode } from 'react'
import { RenderedTheme } from '@/hooks/useDarkTheme'
import type { Theme } from '@/lib/theme'

/** Hands the cookie's theme to the theme switches, so the server renders them as the page is. */
export function ThemeProvider({ theme, children }: { theme: Theme; children: ReactNode }) {
  return <RenderedTheme value={theme}>{children}</RenderedTheme>
}
