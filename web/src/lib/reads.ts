import { load } from './api'

/** A book read lately, with the item last read in it, as GET /api/me/books/recent lists it. */
export type RecentBook = {
  bookId: string
  title: string
  itemKey: string
  itemNumber: string | null
  itemTitle: string
  readAt: string
}

/** A conversation about a book read lately, as GET /api/me/chat/recent lists it. */
export type RecentChat = {
  id: string
  bookId: string
  firstQuestion: string
  lastActivityAt: string
}

/** The books read lately, and the latest conversations about each. */
export type Recent = { books: RecentBook[]; chats: RecentChat[] }

/** Where a book opens, as GET /api/me/books/{id}/read answers. */
export type BookRead = { itemKey: string; readAt: string }

export const RECENT_BOOKS_PATH = '/api/me/books/recent'

export const readPath = (bookId: string) => `/api/me/books/${bookId}/read`

export const recentChatsPath = (bookIds: string[]) =>
  `/api/me/chat/recent?bookIds=${bookIds.map(encodeURIComponent).join(',')}`

// The query names the conversation the reader opens with; the reader drops it once it is open.
export const CHAT_PARAM = 'chat'

export const chatHref = (bookId: string, sessionId: string) =>
  `/books/${bookId}?${CHAT_PARAM}=${encodeURIComponent(sessionId)}`

/** Said on the window when what 최근 lists may have changed: an item read, a conversation begun or deleted. */
export const RECENT_CHANGED = 'gloss:recent'

export function announceRecent() {
  window.dispatchEvent(new Event(RECENT_CHANGED))
}

/** Each book's conversations, newest first, as they came. */
export function chatsByBook(chats: RecentChat[]): Map<string, RecentChat[]> {
  const byBook = new Map<string, RecentChat[]>()
  for (const chat of chats) byBook.set(chat.bookId, [...(byBook.get(chat.bookId) ?? []), chat])
  return byBook
}

/** The books read lately and the latest conversations about each, read again from the page; null when either fails. */
export async function loadRecent(): Promise<Recent | null> {
  const books = await load<RecentBook[]>(RECENT_BOOKS_PATH)
  if (!books.ok) return null
  if (books.data.length === 0) return { books: [], chats: [] }
  const chats = await load<RecentChat[]>(recentChatsPath(books.data.map((book) => book.bookId)))
  return chats.ok ? { books: books.data, chats: chats.data } : null
}
