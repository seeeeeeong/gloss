import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ShelfSearch } from '@/app/(app)/_components/ShelfSearch'

const books = [
  { id: 'a', title: '운영체제' },
  { id: 'b', title: '클린 코드' },
]

describe('ShelfSearch', () => {
  it('lists every book before anything is typed, each a way into it, and counts them', () => {
    const out = renderToStaticMarkup(<ShelfSearch books={books} onClose={() => {}} />)
    expect(out).toContain('aria-label="전체 책 검색"')
    expect(out).toContain('<a class="hit" href="/books/a">')
    expect(out).toContain('<b>클린 코드</b>')
    expect(out).toContain('<span>2권의 책</span>')
  })

  it('offers 책 추가 when the shelf has nothing to show', () => {
    const out = renderToStaticMarkup(<ShelfSearch books={[]} onClose={() => {}} />)
    expect(out).toContain('<p>검색 결과가 없습니다.</p>')
    expect(out).toContain('<a class="tbtn" href="/books/new">책 추가</a>')
  })
})
