import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { AppShell } from '@/app/(app)/_components/AppShell'
import type { Recent } from '@/lib/reads'

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
const bookId = '0199a3b2-7c4e-7d10-9b1f-2a3c4d5e6f70'
const recent: Recent = {
  books: [
    {
      bookId,
      title: '운영체제',
      itemKey: '1 > 1.1',
      itemNumber: '1.1',
      itemTitle: '프로세스',
      readAt: '2026-10-20T00:00:00Z',
    },
  ],
  chats: [
    {
      id: 's1',
      bookId,
      firstQuestion: '커널이 뭐예요?',
      lastActivityAt: '2026-10-20T00:00:00Z',
    },
  ],
}

function render(path: string, read: Recent = recent) {
  nav.path = path
  return renderToStaticMarkup(
    <AppShell me={me} books={books} recent={read} marks={2}>
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

  it('counts the bookmarks beside 책갈피, and keeps a way to them on the rail', () => {
    const out = render('/me')
    expect(out).toMatch(/<a class="row" href="\/bookmarks">.*?책갈피<\/span><small>2<\/small><\/a>/)
    expect(out.slice(out.indexOf('class="sb-rail"'))).toContain('aria-label="책갈피"')
  })

  it('lists the books read lately under 최근, each with a spine and its latest conversations', () => {
    const out = render('/me')
    expect(out).toContain('<div class="sb-sec" id="recent">최근</div>')
    expect(out).toMatch(
      /<span class="spine tone-(sage|clay|ink|apricot)" aria-hidden="true"><\/span>/,
    )
    expect(out).toContain(`<a class="row" title="운영체제" href="/books/${bookId}">`)
    expect(out).toContain(
      `<ul class="sb-chats"><li><a class="row" title="커널이 뭐예요?" href="/books/${bookId}?chat=s1"><span class="lbl-t">커널이 뭐예요?</span></a></li></ul>`,
    )
    expect(out).not.toContain('내 책')
  })

  it('leaves 최근 out until a book is read', () => {
    const out = render('/me', { books: [], chats: [] })
    expect(out).not.toContain('최근')
    expect(out).not.toContain('class="sb-books"')
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

  it("leaves a book's header to the reader, without crumbs or the header's settings", () => {
    const out = render(`/books/${books[0]?.id}`)
    expect(out).toContain('<div class="top reading">')
    expect(out).not.toContain('class="crumbs"')
    expect(out).not.toContain('class="avatar"')
    expect(render('/books/new')).toContain('<div class="top">')
  })

  it('reads a book beside the rail, with the shelf, a search and the theme on it', () => {
    const out = render(`/books/${books[0]?.id}`)
    const rail = out.slice(out.indexOf('class="sb-rail"'))
    expect(out).toMatch(/^<div class="app rail">/)
    expect(rail).toContain('aria-label="내 서재" data-tip="내 서재"')
    expect(rail).toContain('aria-label="서재 검색"')
    expect(rail).toContain('aria-label="다크 모드로 변경"')
    expect(render('/me')).toMatch(/^<div class="app">/)
  })

  it("gives a phone's drawer beside a book the rail's search and theme, which its header lacks", () => {
    const book = render(`/books/${books[0]?.id}`)
    const drawer = book.slice(0, book.indexOf('class="sb-rail"'))
    expect(drawer).toMatch(/<button type="button" class="row">.*?서재 검색<\/span><\/button>/)
    expect(drawer).toMatch(
      /<button type="button" class="row">.*?다크 모드로 변경<\/span><\/button>/,
    )
    const page = render('/me')
    expect(page.slice(0, page.indexOf('class="sb-rail"'))).not.toContain(
      '<button type="button" class="row">',
    )
  })

  it("ends the header with the theme switch and the reader's letter", () => {
    const out = render('/me')
    expect(out).toContain('aria-label="다크 모드로 변경"')
    expect(out).toContain(
      '<button type="button" class="avatar" aria-label="설정 열기" data-tip="설정">R</button>',
    )
  })
})
