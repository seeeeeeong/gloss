import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ThemeProvider } from '@/app/_components/ThemeProvider'
import { ThemeSwitch } from '@/app/(app)/_components/ThemeSwitch'

const switchIn = (theme: 'system' | 'light' | 'dark') =>
  renderToStaticMarkup(
    <ThemeProvider theme={theme}>
      <ThemeSwitch labelledBy="dark-label" />
    </ThemeProvider>,
  )

describe('ThemeProvider', () => {
  it('renders the switch on the server as the cookie has the theme', () => {
    expect(switchIn('dark')).toContain('aria-checked="true"')
    expect(switchIn('light')).toContain('aria-checked="false"')
    expect(switchIn('system')).toContain('aria-checked="false"')
  })
})
