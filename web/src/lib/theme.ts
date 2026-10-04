// A cookie, not local storage, so the server renders the chosen theme without a flash.
export type Theme = 'system' | 'light' | 'dark'

export const THEME_COOKIE = 'gloss.theme'

const THEMES: readonly Theme[] = ['system', 'light', 'dark']

// A year: the theme is a preference, not a session.
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365

export function themeFrom(cookie: string | undefined): Theme {
  return THEMES.find((theme) => theme === cookie) ?? 'system'
}

export function nextTheme(theme: Theme): Theme {
  return THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length] ?? 'system'
}

export function themeCookie(theme: Theme): string {
  return `${THEME_COOKIE}=${theme}; path=/; max-age=${COOKIE_MAX_AGE}; samesite=lax`
}

export function themeAttribute(theme: Theme): 'light' | 'dark' | undefined {
  return theme === 'system' ? undefined : theme
}

// --bg-canvas in globals.css.
const CANVAS = { light: '#ffffff', dark: '#111111' } as const

export function themeColors(theme: Theme): { media: string; color: string }[] {
  return (['light', 'dark'] as const).map((scheme) => ({
    media: `(prefers-color-scheme: ${scheme})`,
    color: CANVAS[theme === 'system' ? scheme : theme],
  }))
}

export const THEME_LABELS: Record<Theme, string> = {
  system: '시스템 설정',
  light: '라이트',
  dark: '다크',
}
