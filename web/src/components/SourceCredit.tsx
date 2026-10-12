import { type Book, creditOf } from '@/lib/books'

/** Names where a book's details came from and links its page there, as the source's terms ask. */
export function SourceCredit({ book }: { book: Pick<Book, 'source' | 'sourceUrl'> }) {
  const credit = creditOf(book.source)
  if (!credit || !book.sourceUrl) return null
  return (
    <a href={book.sourceUrl} target="_blank" rel="noopener noreferrer">
      {credit}
    </a>
  )
}
