import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { cache } from 'react'
import { TocReview } from '@/components/TocReview'
import { TocTree } from '@/components/TocTree'
import { formatDateTime } from '@/lib/accounts'
import { type Book, bylineOf, isBookId, tocStatusLabel, treeOf } from '@/lib/books'
import { serverFind } from '@/lib/server-session'

// Once a request: the title and the page ask for the same book.
const bookOf = cache(async (bookId: string) =>
  isBookId(bookId) ? serverFind<Book>(`/api/me/books/${bookId}`) : null,
)

export async function generateMetadata({
  params,
}: PageProps<'/books/[bookId]'>): Promise<Metadata> {
  const book = await bookOf((await params).bookId)
  return { title: book?.title ?? '책' }
}

export default async function BookPage({ params }: PageProps<'/books/[bookId]'>) {
  const book = await bookOf((await params).bookId)
  if (!book) notFound()
  const { toc } = book
  const byline = bylineOf(book)
  return (
    <div className="page">
      <h1 className="page-title">{book.title}</h1>
      <p className="page-lead">
        {byline}
        {byline && book.isbn13 && ' · '}
        {book.isbn13 && `ISBN ${book.isbn13}`}
      </p>
      <section className="section" aria-labelledby="toc">
        <h2 className="section-title" id="toc">
          목차
          <span className="tag">
            버전 {toc.versionNo} · {tocStatusLabel(toc.status)}
          </span>
        </h2>
        <p className="field-hint toc-facts">
          항목 {toc.items.length}개{toc.confirmedAt && ` · 확정 ${formatDateTime(toc.confirmedAt)}`}
        </p>
        <TocReview
          bookId={book.id}
          toc={{ id: toc.id, status: toc.status, raw: toc.raw }}
          replacing={book.currentTocId !== null && book.currentTocId !== toc.id}
        >
          <TocTree nodes={treeOf(toc.items)} />
        </TocReview>
      </section>
    </div>
  )
}
