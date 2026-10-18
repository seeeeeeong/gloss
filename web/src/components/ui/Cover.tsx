import './cover.css'
import { authorsLine, type BookSummary } from '@/lib/books'
import { monogramOf, toneOf } from '@/lib/shelf'

/** A jacket drawn from the book's title and authors, in its shelf tone. */
export function Cover({ book }: { book: Pick<BookSummary, 'id' | 'title' | 'authors'> }) {
  return (
    <div className={`jacket tone-${toneOf(book.id)}`} aria-hidden="true">
      <div className="jacket-top">
        <span>gloss</span>
      </div>
      <strong>{book.title}</strong>
      <div className="jacket-art">
        <span>{monogramOf(book.title)}</span>
        <i />
        <i />
        <i />
        <i />
        <i />
      </div>
      <span className="jacket-author">{authorsLine(book.authors)}</span>
    </div>
  )
}
