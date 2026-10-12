import type { TocItem } from './books'

/** Which sections of a book have a post, as GET /api/me/books/{id}/posts lists them. */
export type PostSummary = {
  itemKey: string
  versionNo: number
  stale: boolean
  generatedAt: string
}

/** A post as a reader sees it: its current version. */
export type Post = {
  id: string
  itemKey: string
  versionId: string
  versionNo: number
  stale: boolean
  contentMd: string
  model: string
  generatedAt: string
}

// The key rides in the query, never the path: it is made from a title, so it can hold a slash.
export const postHref = (bookId: string, itemKey: string) =>
  `/books/${bookId}/post?item=${encodeURIComponent(itemKey)}`

export const postPath = (bookId: string, itemKey: string) =>
  `/api/me/books/${bookId}/post?itemKey=${encodeURIComponent(itemKey)}`

export const headingOf = (item: Pick<TocItem, 'number' | 'title'>) =>
  item.number ? `${item.number} ${item.title}` : item.title

export type Place = { item: TocItem; previous: TocItem | null; next: TocItem | null }

/** A section with the ones before and after it in reading order; null when the contents have no such key. */
export function placeOf(items: TocItem[], itemKey: string): Place | null {
  const at = items.findIndex((item) => item.itemKey === itemKey)
  const item = items[at]
  if (!item) return null
  return { item, previous: items[at - 1] ?? null, next: items[at + 1] ?? null }
}
