import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { Bookmarks } from '@/components/Bookmarks'
import type { Bookmark } from '@/lib/bookmarks'

vi.mock('next/navigation', () => ({ useRouter: () => ({ refresh() {} }) }))

const mark = (changes: Partial<Bookmark> = {}): Bookmark => ({
  id: 'm1',
  bookId: 'b1',
  bookTitle: '클린 코드',
  itemKey: '2 > 2.1',
  itemNumber: '2.1',
  itemTitle: '의미 있는 이름',
  quote: '이름은 코드의 첫 번째 설명이다.',
  createdAt: '2026-10-18T00:00:00Z',
  ...changes,
})

describe('Bookmarks', () => {
  it('shows each phrase large with its book and item, which open it, and a way to let it go', () => {
    const out = renderToStaticMarkup(<Bookmarks bookmarks={[mark()]} />)
    expect(out).toContain('<h1>책갈피<span class="count">1</span></h1>')
    expect(out).toContain('<blockquote>이름은 코드의 첫 번째 설명이다.</blockquote>')
    expect(out).toContain('<a class="mark-from" href="/books/b1?item=2%20%3E%202.1">')
    expect(out).toContain('<b>클린 코드</b><small>2.1 의미 있는 이름</small>')
    expect(out).toContain('aria-label="이름은 코드의 첫 번째 설명이다. 책갈피 지우기"')
  })

  it('shows an item kept whole by its numbered title', () => {
    const out = renderToStaticMarkup(<Bookmarks bookmarks={[mark({ quote: null })]} />)
    expect(out).toContain('<blockquote>2.1 의미 있는 이름</blockquote>')
  })

  it('keeps a quiet status line for what a delete says', () => {
    const out = renderToStaticMarkup(<Bookmarks bookmarks={[mark()]} />)
    expect(out).toContain('<div class="toast" role="status"></div>')
  })

  it('offers reading when nothing is kept', () => {
    const out = renderToStaticMarkup(<Bookmarks bookmarks={[]} />)
    expect(out).toContain('<h2>저장한 문장이 없습니다</h2>')
    expect(out).toContain('<a class="btn primary" href="/">글 읽기</a>')
  })
})
