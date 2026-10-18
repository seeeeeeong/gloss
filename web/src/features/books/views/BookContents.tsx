import { Page } from '@/components/ui/Page'
import { SourceCredit } from '@/components/ui/SourceCredit'
import { TocReview } from '@/features/books/components/TocReview'
import { TocTree } from '@/features/books/components/TocTree'
import {
  type Book,
  bylineOf,
  contentsSectionsOf,
  type Toc,
  tocStatusLabel,
  treeOf,
} from '@/lib/books'
import { formatDateTime } from '@/utils/format'

/** A book's contents to review: the version in use, a newer draft, and editing and confirming. */
export function BookContents({ book, inUse }: { book: Book; inUse: Toc | null }) {
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
      {contentsSectionsOf(inUse, book.toc).map(({ id, title, toc, review }) => (
        <section key={id} className="toc-sec" aria-labelledby={id}>
          <h2 className="sub-h" id={id}>
            {title}
            <span className="spill">
              버전 {toc.versionNo} · {tocStatusLabel(toc.status)}
            </span>
          </h2>
          <p className="toc-facts">
            항목 {toc.items.length}개
            {toc.confirmedAt && ` · 확정 ${formatDateTime(toc.confirmedAt)}`}
          </p>
          {review ? (
            <TocReview bookId={book.id} toc={{ id: toc.id, status: toc.status, raw: toc.raw }}>
              <TocTree nodes={treeOf(toc.items)} />
            </TocReview>
          ) : (
            <div className="toc">
              <TocTree nodes={treeOf(toc.items)} />
            </div>
          )}
        </section>
      ))}
    </Page>
  )
}
