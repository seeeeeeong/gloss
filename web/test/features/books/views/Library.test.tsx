import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { Continue, Library } from '@/features/books/views/Library'
import type { BookSummary } from '@/lib/books'

const book = (id: string, changes: Partial<BookSummary> = {}): BookSummary => ({
  id,
  title: '운영체제',
  authors: ['Abraham Silberschatz', 'Peter B. Galvin'],
  publisher: '퍼스트북',
  isbn13: null,
  publishedOn: '2020-02-28',
  kind: '도서',
  source: 'yes24',
  sourceUrl: null,
  confirmed: true,
  createdAt: '2026-10-01T00:00:00Z',
  ...changes,
})

describe('Library', () => {
  it('heads the shelf with its count and 책 추가, and lists every book as a cover', () => {
    const out = renderToStaticMarkup(
      <Library
        books={[
          book('a'),
          book('b', { title: '클린 코드', publisher: null, publishedOn: null, kind: null }),
        ]}
      />,
    )
    expect(out).toContain('<h1>내 서재<span class="count">2</span></h1>')
    expect(out).toMatch(/<a class="btn primary" href="\/books\/new">.*책 추가<\/a>/)
    expect(out).toContain('모든 책<span>2</span>')
    expect(out).toContain('<ul class="shelf-books grid">')
    expect(out).toContain('<a class="book-card" href="/books/a">')
    expect(out).toContain('<div class="book-meta"><span>퍼스트북</span><span>2020</span></div>')
    expect(out.match(/class="book-meta"/g)).toHaveLength(1)
  })

  it('offers 책 추가 on an empty shelf, with nothing to search', () => {
    const out = renderToStaticMarkup(<Library books={[]} />)
    expect(out).toContain('<h2>책이 없습니다</h2>')
    expect(out).not.toContain('<ul class="shelf-books')
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

describe('Library with a read', () => {
  it('offers the item read last, wherever it was read', () => {
    const latest = { bookId: 'b', itemKey: 'k', title: '스레드', position: 2, total: 5 }
    const out = renderToStaticMarkup(
      <Library books={[book('a'), book('b')]} recent={['b', 'a']} latest={latest} />,
    )
    expect(out).toContain('<a class="continue" href="/books/b?item=k">')
    expect(out).toContain('02 / 05')
  })
})

describe('Library with a bookmark', () => {
  it('shows the bookmark kept last, which opens every bookmark', () => {
    const mark = {
      id: 'm1',
      bookId: 'a',
      bookTitle: '운영체제',
      itemKey: 'k',
      itemNumber: null,
      itemTitle: '프로세스',
      quote: '프로세스는 실행 중인 프로그램이다.',
      createdAt: '2026-10-18T00:00:00Z',
    }
    const out = renderToStaticMarkup(<Library books={[book('a')]} mark={mark} />)
    expect(out).toContain('<section class="continue-row" aria-label="최근 활동">')
    expect(out).toContain('<a class="last-mark" href="/bookmarks">')
    expect(out).toContain('<blockquote>프로세스는 실행 중인 프로그램이다.</blockquote>')
  })
})
