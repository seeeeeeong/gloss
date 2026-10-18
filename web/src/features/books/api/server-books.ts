import { unstable_rethrow } from 'next/navigation'
import { cache } from 'react'
import { BOOKMARKS_PATH, type Bookmark } from '@/lib/bookmarks'
import { type Book, type BookSummary, isBookId, type Toc } from '@/lib/books'
import { serverFind, serverGet } from '@/lib/server-session'

/** Once a request: the sidebar and the shelf list the same books. */
export const shelfOf = cache(() => serverGet<BookSummary[]>('/api/me/books'))

/** Once a request: the sidebar counts the bookmarks the shelf and their page show. */
export const bookmarksOf = cache(() => serverGet<Bookmark[]>(BOOKMARKS_PATH))

/**
 * The bookmarks for a page that shows them beside its own content, as the sidebar's count does: none
 * when the server cannot list them, so the page still opens. A lost session still goes to sign-in.
 */
export async function bookmarksBeside(): Promise<Bookmark[]> {
  try {
    return await bookmarksOf()
  } catch (error) {
    unstable_rethrow(error)
    return []
  }
}

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
