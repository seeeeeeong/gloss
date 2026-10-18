'use client'

import { useCallback, useSyncExternalStore } from 'react'
import { type Theme, themeAttribute, themeColors, themeCookie } from '@/lib/theme'

const DARK = '(prefers-color-scheme: dark)'

/** Shows [theme] now and keeps it in the cookie the server renders the next page with. */
export function applyTheme(theme: Theme) {
  const attribute = themeAttribute(theme)
  if (attribute) document.documentElement.dataset.theme = attribute
  else delete document.documentElement.dataset.theme
  // biome-ignore lint/suspicious/noDocumentCookie: the Cookie Store API is not in Safari
  document.cookie = themeCookie(theme)
  for (const { media, color } of themeColors(theme)) {
    document
      .querySelector(`meta[name="theme-color"][media="${media}"]`)
      ?.setAttribute('content', color)
  }
}

function subscribe(onChange: () => void) {
  const media = matchMedia(DARK)
  const observer = new MutationObserver(onChange)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  media.addEventListener('change', onChange)
  return () => {
    observer.disconnect()
    media.removeEventListener('change', onChange)
  }
}

function chosenTheme(): Theme {
  const chosen = document.documentElement.dataset.theme
  return chosen === 'light' || chosen === 'dark' ? chosen : 'system'
}

function isDark() {
  const chosen = chosenTheme()
  return chosen === 'system' ? matchMedia(DARK).matches : chosen === 'dark'
}

/** The theme chosen on the page now, [initial] while the server renders it. */
export function useThemeChoice(initial: Theme): Theme {
  return useSyncExternalStore(subscribe, chosenTheme, () => initial)
}

/**
 * Whether the page is dark now, however it was chosen, and a switch to the other. It reads the page
 * itself, so a theme set elsewhere, as on 마이페이지, shows here at once.
 */
export function useDarkTheme() {
  const dark = useSyncExternalStore(subscribe, isDark, () => false)
  const toggle = useCallback(() => applyTheme(isDark() ? 'light' : 'dark'), [])
  return { dark, toggle }
}
