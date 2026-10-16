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
export const readerHref = (bookId: string, itemKey: string) =>
  `/books/${bookId}?item=${encodeURIComponent(itemKey)}`

export const postPath = (bookId: string, itemKey: string) =>
  `/api/me/books/${bookId}/post?itemKey=${encodeURIComponent(itemKey)}`

/** How far a section's post has come, as POST /api/me/books/{id}/post/generation answers. */
export type Generation = {
  state: string
  code: string | null
  message: string | null
}

/** Asking is what moves the work on; `retry` asks again for work that failed. */
export const generationPath = (bookId: string, itemKey: string, retry = false) =>
  `/api/me/books/${bookId}/post/generation?itemKey=${encodeURIComponent(itemKey)}${retry ? '&retry=true' : ''}`

/** While the plan or the post is being written the page asks again; any other state is where it stops. */
export const isUnderway = (state: string) => state === 'planning' || state === 'writing'

export const headingOf = (item: Pick<TocItem, 'number' | 'title'>) =>
  item.number ? `${item.number} ${item.title}` : item.title
