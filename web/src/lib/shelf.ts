/** A book's color on the shelf, its sidebar spine and its cover: the same for every reader. */
export const TONES = ['sage', 'clay', 'ink', 'apricot'] as const
export type Tone = (typeof TONES)[number]

export function toneOf(bookId: string): Tone {
  let hash = 0
  for (const char of bookId) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return TONES[hash % TONES.length] ?? 'sage'
}

/** The letter a cover is drawn around: the title's first, whole even when it is a pair of surrogates. */
export const monogramOf = (title: string) => Array.from(title.trim())[0] ?? ''

/** The item last read in a book, as the reader left it, for the shelf to offer again. */
export type RecentRead = {
  bookId: string
  itemKey: string
  title: string
  /** The item's place in the contents, from 1, and how many items there are. */
  position: number
  total: number
}

export const RECENT_KEY = 'gloss.recent'

// Enough for the shelf's "최근 읽은 책" without growing with every book ever opened.
const RECENT_MAX = 20

/** The reads kept, newest first and one per book, with [read] in front. */
export function withRead(reads: RecentRead[], read: RecentRead): RecentRead[] {
  return [read, ...reads.filter((kept) => kept.bookId !== read.bookId)].slice(0, RECENT_MAX)
}

/** The reads in storage; anything that is not one, or not there, reads as none. */
export function readsFrom(stored: string | null): RecentRead[] {
  if (!stored) return []
  try {
    const parsed: unknown = JSON.parse(stored)
    return Array.isArray(parsed) ? parsed.filter(isRead) : []
  } catch {
    return []
  }
}

function isRead(value: unknown): value is RecentRead {
  if (typeof value !== 'object' || value === null) return false
  const read = value as Record<string, unknown>
  return (
    typeof read.bookId === 'string' &&
    typeof read.itemKey === 'string' &&
    typeof read.title === 'string' &&
    Number.isInteger(read.position) &&
    Number.isInteger(read.total)
  )
}

/** A place in the contents as the shelf writes it: "03 / 08". */
export const placeOf = (read: Pick<RecentRead, 'position' | 'total'>) =>
  `${String(read.position).padStart(2, '0')} / ${String(read.total).padStart(2, '0')}`

/** Books whose title or authors hold [query], ignoring case and the spaces around it. */
export function matching<T extends { title: string; authors: string[] }>(
  books: T[],
  query: string,
): T[] {
  const wanted = query.trim().toLowerCase()
  if (!wanted) return books
  return books.filter((book) =>
    `${book.title} ${book.authors.join(' ')}`.toLowerCase().includes(wanted),
  )
}
