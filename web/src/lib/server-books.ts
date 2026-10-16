import { cache } from 'react'
import { type Book, type BookSummary, isBookId, type Toc } from './books'
import { serverFind, serverGet } from './server-session'

/** Once a request: the sidebar and the shelf list the same books. */
export const shelfOf = cache(() => serverGet<BookSummary[]>('/api/me/books'))

/** Once a request: the title and the page ask for the same book. */
export const bookOf = cache(async (bookId: string) =>
  isBookId(bookId) ? serverFind<Book>(`/api/me/books/${bookId}`) : null,
)

/**
 * The contents posts are written from, or null until one is confirmed. The book carries its
 * latest version, which is this one unless a newer draft is waiting.
 */
export async function tocInUse(book: Book): Promise<Toc | null> {
  if (book.currentTocId === null) return null
  if (book.currentTocId === book.toc.id) return book.toc
  return serverFind<Toc>(`/api/me/books/${book.id}/toc/current`)
}
