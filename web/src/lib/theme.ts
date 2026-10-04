/**
 * The reader's color theme. `system` follows the device; `light` and `dark` override
 * it. A cookie, not local storage: the server renders the page in the chosen theme
 * instead of the page flashing the other one after load.
 */
export type Theme = 'system' | 'light' | 'dark'

export const THEME_COOKIE = 'gloss.theme'

/** The order the toggle steps through. */
const THEMES: readonly Theme[] = ['system', 'light', 'dark']

/** A year: the theme is a preference, not a session. */
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365

/** A stored theme the page can use, or `system`: the cookie is the reader's to edit. */
export function themeFrom(cookie: string | undefined): Theme {
  return THEMES.find((theme) => theme === cookie) ?? 'system'
}

export function nextTheme(theme: Theme): Theme {
  return THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length] ?? 'system'
}

export function themeCookie(theme: Theme): string {
  return `${THEME_COOKIE}=${theme}; path=/; max-age=${COOKIE_MAX_AGE}; samesite=lax`
}

/** What the root element carries: nothing for `system`, which the stylesheet reads from the device. */
export function themeAttribute(theme: Theme): 'light' | 'dark' | undefined {
  return theme === 'system' ? undefined : theme
}

/** --bg-canvas in each palette (globals.css): the browser's top bar matches the page. */
const CANVAS = { light: '#ffffff', dark: '#111111' } as const

/**
 * The theme-color for each device scheme. A chosen theme paints both the same, or a
 * light page would sit under dark browser chrome on a dark device, and the reverse.
 */
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
