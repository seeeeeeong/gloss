import { unstable_rethrow } from 'next/navigation'
import { cache } from 'react'
import { BOOKMARKS_PATH, type Bookmark } from '@/lib/bookmarks'
import { type Book, type BookSummary, isBookId, type Toc } from '@/lib/books'
import {
  type BookRead,
  RECENT_BOOKS_PATH,
  type Recent,
  type RecentBook,
  type RecentChat,
  readPath,
  recentChatsPath,
} from '@/lib/reads'
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

/**
 * Once a request, as the sidebar and the shelf list the same: the books read lately and the latest
 * conversations about each, or none of either when the server cannot list them, so the page still opens.
 */
export const recentBeside = cache(async (): Promise<Recent> => {
  try {
    const books = await serverGet<RecentBook[]>(RECENT_BOOKS_PATH)
    if (books.length === 0) return { books, chats: [] }
    const chats = await serverGet<RecentChat[]>(recentChatsPath(books.map((book) => book.bookId)))
    return { books, chats }
  } catch (error) {
    unstable_rethrow(error)
    return { books: [], chats: [] }
  }
})

/**
 * Where the reader last was in a book: null before they have read in it, or when the server cannot
 * say, so the book still opens at its first item.
 */
export async function lastReadOf(bookId: string): Promise<BookRead | null> {
  try {
    return await serverFind<BookRead>(readPath(bookId))
  } catch (error) {
    unstable_rethrow(error)
    return null
  }
}

type PostCount = { bookId: string; posts: number }

/**
 * How many posts each book on the shelf has, by its id; a book with none is missing. Empty when the
 * server cannot count them, so the shelf still opens. A lost session still goes to sign-in.
 */
export async function postCountsBeside(): Promise<Record<string, number>> {
  try {
    const counts = await serverGet<PostCount[]>('/api/me/post-counts')
    return Object.fromEntries(counts.map((count) => [count.bookId, count.posts]))
  } catch (error) {
    unstable_rethrow(error)
    return {}
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
