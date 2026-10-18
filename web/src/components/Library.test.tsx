import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { BookSummary } from '@/lib/books'
import { Continue, Cover, Library } from './Library'

const book = (id: string, changes: Partial<BookSummary> = {}): BookSummary => ({
  id,
  title: '운영체제',
  authors: ['Abraham Silberschatz', 'Peter B. Galvin'],
  publisher: '퍼스트북',
  isbn13: null,
  source: 'yes24',
  sourceUrl: null,
  confirmed: true,
  createdAt: '2026-10-01T00:00:00Z',
  ...changes,
})

describe('Library', () => {
  it('heads the shelf with its count and 책 추가, and lists every book as a cover', () => {
    const out = renderToStaticMarkup(
      <Library books={[book('a'), book('b', { title: '클린 코드', publisher: null })]} />,
    )
    expect(out).toContain('<h1>내 서재<span class="count">2</span></h1>')
    expect(out).toMatch(/<a class="btn primary" href="\/books\/new">.*책 추가<\/a>/)
    expect(out).toContain('모든 책<span>2</span>')
    expect(out).toContain('<ul class="shelf-books grid">')
    expect(out).toContain('<a class="book-card" href="/books/a">')
    expect(out).toContain('<div class="book-meta"><span>퍼스트북</span></div>')
    expect(out.match(/class="book-meta"/g)).toHaveLength(1)
  })

  it('offers 책 추가 on an empty shelf, with nothing to search', () => {
    const out = renderToStaticMarkup(<Library books={[]} />)
    expect(out).toContain('<h2>책이 없습니다</h2>')
    expect(out).not.toContain('<ul class="shelf-books')
  })
})

describe('Cover', () => {
  it("draws the title, its first letter large and the authors, in the book's tone", () => {
    const out = renderToStaticMarkup(<Cover book={book('a')} />)
    expect(out).toMatch(/<div class="jacket tone-(sage|clay|ink|apricot)" aria-hidden="true">/)
    expect(out).toContain('<strong>운영체제</strong>')
    expect(out).toContain('<div class="jacket-art"><span>운</span>')
    expect(out).toContain(
      '<span class="jacket-author">Abraham Silberschatz, Peter B. Galvin</span>',
    )
  })
})

describe('Continue', () => {
  it('opens the book at the item last read, with where it sits in the contents', () => {
    const read = { bookId: 'a', itemKey: '1 > 3', title: '프로세스의 이해', position: 3, total: 8 }
    const out = renderToStaticMarkup(<Continue read={read} book={book('a')} />)
    expect(out).toContain('<a class="continue" href="/books/a?item=1%20%3E%203">')
    expect(out).toContain('<h2>프로세스의 이해</h2>')
    expect(out).toContain('03 / 08')
  })
})
