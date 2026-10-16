import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import type { ReactNode } from 'react'
import { Page } from '@/components/Page'
import { SourceCredit } from '@/components/SourceCredit'
import { TocReview } from '@/components/TocReview'
import { TocTree } from '@/components/TocTree'
import { formatDateTime } from '@/lib/accounts'
import { bylineOf, type Toc, tocStatusLabel, treeOf } from '@/lib/books'
import type { PostSummary } from '@/lib/posts'
import { bookOf, tocInUse } from '@/lib/server-books'
import { serverGet } from '@/lib/server-session'

export async function generateMetadata({
  params,
}: PageProps<'/books/[bookId]'>): Promise<Metadata> {
  const book = await bookOf((await params).bookId)
  return { title: book?.title ?? '책' }
}

export default async function BookPage({ params }: PageProps<'/books/[bookId]'>) {
  const book = await bookOf((await params).bookId)
  if (!book) notFound()
  const latest = book.toc
  const inUse = await tocInUse(book)
  const posts = inUse ? await serverGet<PostSummary[]>(`/api/me/books/${book.id}/posts`) : []
  const reading = { bookId: book.id, written: new Set(posts.map((post) => post.itemKey)) }
  const byline = bylineOf(book)
  return (
    <Page>
      <h1 className="title">{book.title}</h1>
      <p className="meta">
        {byline}
        {byline && book.isbn13 && ' · '}
        {book.isbn13 && `ISBN ${book.isbn13}`}
        {(byline || book.isbn13) && book.sourceUrl && ' · '}
        <SourceCredit book={book} />
      </p>
      {inUse && (
        <TocSection id="toc" title="목차" toc={inUse}>
          {inUse.id === latest.id ? (
            <TocReview
              bookId={book.id}
              toc={{ id: inUse.id, status: inUse.status, raw: inUse.raw }}
            >
              <TocTree nodes={treeOf(inUse.items)} reading={reading} />
            </TocReview>
          ) : (
            <div className="toc">
              <TocTree nodes={treeOf(inUse.items)} reading={reading} />
            </div>
          )}
        </TocSection>
      )}
      {inUse?.id !== latest.id && (
        <TocSection id="draft" title={inUse ? '새 목차' : '목차'} toc={latest}>
          <TocReview
            bookId={book.id}
            toc={{ id: latest.id, status: latest.status, raw: latest.raw }}
          >
            <TocTree nodes={treeOf(latest.items)} />
          </TocReview>
        </TocSection>
      )}
    </Page>
  )
}

function TocSection(props: { id: string; title: string; toc: Toc; children: ReactNode }) {
  const { id, title, toc, children } = props
  return (
    <section className="toc-sec" aria-labelledby={id}>
      <h2 className="sub-h" id={id}>
        {title}
        <span className="spill">
          버전 {toc.versionNo} · {tocStatusLabel(toc.status)}
        </span>
      </h2>
      <p className="toc-facts">
        항목 {toc.items.length}개{toc.confirmedAt && ` · 확정 ${formatDateTime(toc.confirmedAt)}`}
      </p>
      {children}
    </section>
  )
}
