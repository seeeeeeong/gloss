import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { AppShell } from './AppShell'

const nav = vi.hoisted(() => ({ path: '/me' }))
vi.mock('next/navigation', () => ({ usePathname: () => nav.path }))

const me = {
  id: '1',
  loginId: 'reader',
  displayName: 'Reader',
  role: 'operator',
  mustChangePassword: false,
  lastLoginAt: null,
}

const books = [{ id: '0199a3b2-7c4e-7d10-9b1f-2a3c4d5e6f70', title: '운영체제' }]

function render(path: string) {
  nav.path = path
  return renderToStaticMarkup(
    <AppShell me={me} books={books}>
      <p>page</p>
    </AppShell>,
  )
}

describe('AppShell', () => {
  it('leads with the brand back to the shelf, then 내 서재 and 책 추가', () => {
    const out = render('/me')
    expect(out).toContain('<a class="brand" aria-label="Gloss 내 서재" href="/">')
    expect(out).toMatch(/<a class="row" href="\/">.*?내 서재<\/span><\/a>/)
    expect(out).toMatch(/<a class="row" href="\/books\/new">.*?책 추가<\/span><\/a>/)
  })

  it('lists the books under 내 책 with their count and a spine in their tone', () => {
    const out = render('/me')
    expect(out).toContain('내 책<span>1</span>')
    expect(out).toMatch(
      /<span class="spine tone-(sage|clay|ink|apricot)" aria-hidden="true"><\/span>/,
    )
    expect(out).toContain('<span class="lbl-t">운영체제</span>')
  })

  it('marks the current page in the sidebar, but not on the rail', () => {
    const out = render('/me')
    const rail = out.slice(out.indexOf('class="sb-rail"'))
    const full = out.slice(0, out.indexOf('class="sb-rail"'))

    expect(full).toMatch(
      /<a class="row" aria-current="page" href="\/me">|<a class="row" href="\/me" aria-current="page">/,
    )
    expect(rail).not.toContain('aria-current="page" href="/me"')
    expect(rail).not.toMatch(/href="\/me"[^>]*aria-current/)
  })

  it('names where a screen sits in the header, under the shelf or on its own', () => {
    expect(render('/')).toContain(
      '<p class="crumbs"><span>서재</span><span aria-hidden="true">/</span><span class="here">내 서재</span></p>',
    )
    expect(render('/me')).toContain('<p class="crumbs"><span class="here">마이페이지</span></p>')
  })

  it("leaves a book's header to the reader, transparent and without crumbs or the header's settings", () => {
    const out = render(`/books/${books[0]?.id}`)
    expect(out).toContain('<div class="top reading">')
    expect(out).not.toContain('class="crumbs"')
    expect(out).not.toContain('class="avatar"')
    expect(render('/books/new')).toContain('<div class="top">')
  })

  it("ends the header with the theme switch and the reader's letter", () => {
    const out = render('/me')
    expect(out).toContain('aria-label="다크 모드로 변경"')
    expect(out).toContain(
      '<button type="button" class="avatar" aria-label="설정 열기" data-tip="설정">R</button>',
    )
  })
})
