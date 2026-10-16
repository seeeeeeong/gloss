import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'

vi.mock('next/navigation', () => ({ usePathname: () => '/me' }))

const me = {
  id: '1',
  loginId: 'reader',
  displayName: 'Reader',
  role: 'operator',
  mustChangePassword: false,
  lastLoginAt: null,
}

describe('AppShell', () => {
  const out = renderToStaticMarkup(
    <AppShell me={me} books={[]}>
      <p>page</p>
    </AppShell>,
  )

  it('shows the wordmark as a name, not a link, and sends the header title to a new book', () => {
    expect(out).toContain('<span class="wordmark">Gloss</span>')
    expect(out).toContain('<a class="top-title" href="/books/new">Gloss</a>')
    expect(out).toContain('<a class="name" href="/books/new">Gloss</a>')
    expect(out).not.toContain('href="/"')
  })

  it('marks the current page in the sidebar, but not on the rail', () => {
    const rail = out.slice(out.indexOf('class="sb-rail"'))
    const full = out.slice(0, out.indexOf('class="sb-rail"'))

    expect(full).toMatch(
      /<a class="row" aria-current="page" href="\/me">|<a class="row" href="\/me" aria-current="page">/,
    )
    expect(rail).not.toContain('aria-current="page" href="/me"')
    expect(rail).not.toMatch(/href="\/me"[^>]*aria-current/)
  })
})
