import type { Metadata } from 'next'
import Link from 'next/link'
import { Page } from '@/components/Page'
import { bylineOf, creditOf } from '@/lib/books'
import { shelfOf } from '@/lib/server-books'

export const metadata: Metadata = { title: '책장' }

export default async function ShelfPage() {
  const books = await shelfOf()
  return (
    <Page>
      <div className="p-row">
        <h1 className="title">책장</h1>
        <Link className="btn primary" href="/books/new">
          새 책
        </Link>
      </div>
      {books.length === 0 ? (
        <p className="empty-l">아직 책이 없습니다.</p>
      ) : (
        <ul className="list">
          {books.map((book) => (
            <li key={book.id}>
              <Link className="li-row" href={`/books/${book.id}`}>
                <span className="li-main">
                  <span className="nm">{book.title}</span>
                  <span className="lid">
                    {[bylineOf(book), creditOf(book.source)].filter(Boolean).join(' · ')}
                  </span>
                </span>
                {!book.confirmed && <span className="spill">목차 확정 전</span>}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Page>
  )
}
