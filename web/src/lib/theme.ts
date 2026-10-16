// A cookie, not local storage, so the server renders the chosen theme without a flash.
export type Theme = 'system' | 'light' | 'dark'

export const THEME_COOKIE = 'gloss.theme'

export const THEMES: readonly Theme[] = ['system', 'light', 'dark']

// A year: the theme is a preference, not a session.
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365

export function themeFrom(cookie: string | undefined): Theme {
  return THEMES.find((theme) => theme === cookie) ?? 'system'
}

export function themeCookie(theme: Theme): string {
  return `${THEME_COOKIE}=${theme}; path=/; max-age=${COOKIE_MAX_AGE}; samesite=lax`
}

export function themeAttribute(theme: Theme): 'light' | 'dark' | undefined {
  return theme === 'system' ? undefined : theme
}

// --page in globals.css.
const CANVAS = { light: '#fcfcfc', dark: '#000000' } as const

export function themeColors(theme: Theme): { media: string; color: string }[] {
  return (['light', 'dark'] as const).map((scheme) => ({
    media: `(prefers-color-scheme: ${scheme})`,
    color: CANVAS[theme === 'system' ? scheme : theme],
  }))
}

export const THEME_LABELS: Record<Theme, string> = {
  system: '시스템',
  light: '라이트',
  dark: '다크',
}
