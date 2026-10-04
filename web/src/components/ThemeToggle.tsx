'use client'

import { useEffect, useState } from 'react'
import {
  nextTheme,
  THEME_LABELS,
  type Theme,
  themeAttribute,
  themeColors,
  themeCookie,
} from '@/lib/theme'
import { Icon } from './Icon'

const ICONS = { system: 'monitor', light: 'sun', dark: 'moon' } as const

/** Steps the theme through system, light and dark. The server already rendered the stored one. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>('system')

  useEffect(() => {
    const stored = document.documentElement.dataset.theme
    setTheme(stored === 'light' || stored === 'dark' ? stored : 'system')
  }, [])

  function step() {
    const next = nextTheme(theme)
    const attribute = themeAttribute(next)
    if (attribute) document.documentElement.dataset.theme = attribute
    else delete document.documentElement.dataset.theme
    // biome-ignore lint/suspicious/noDocumentCookie: the Cookie Store API is not in Safari
    document.cookie = themeCookie(next)
    for (const { media, color } of themeColors(next)) {
      document
        .querySelector(`meta[name="theme-color"][media="${media}"]`)
        ?.setAttribute('content', color)
    }
    setTheme(next)
  }

  const label = `테마: ${THEME_LABELS[theme]} (눌러서 ${THEME_LABELS[nextTheme(theme)]})`
  return (
    <button type="button" className="icon-btn" title={label} aria-label={label} onClick={step}>
      <Icon name={ICONS[theme]} />
    </button>
  )
}
