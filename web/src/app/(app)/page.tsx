import type { Metadata } from 'next'
import Link from 'next/link'
import { Icon } from '@/components/Icon'
import { type BookSummary, bylineOf } from '@/lib/books'
import { serverGet } from '@/lib/server-session'

export const metadata: Metadata = { title: '내 책장' }

export default async function ShelfPage() {
  const books = await serverGet<BookSummary[]>('/api/me/books')
  return (
    <div className="page">
      <div className="page-head">
        <h1 className="page-title">내 책장</h1>
        <Link className="btn btn-primary" href="/books/new">
          <Icon name="plus" size={16} />책 추가
        </Link>
      </div>
      {books.length === 0 ? (
        <div className="empty">아직 책이 없습니다.</div>
      ) : (
        <ul className="shelf">
          {books.map((book) => (
            <li key={book.id}>
              <Link className="shelf-item" href={`/books/${book.id}`}>
                <span className="shelf-title">{book.title}</span>
                <span className="shelf-meta">
                  {bylineOf(book)}
                  {!book.confirmed && <span className="tag">목차 확정 전</span>}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
