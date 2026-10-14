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

/** How far a section's post has come, as POST /api/me/books/{id}/post/generation answers. */
export type Generation = {
  state: string
  code: string | null
  message: string | null
}

/** Asking is what moves the work on; `retry` asks again for work that failed. */
export const generationPath = (bookId: string, itemKey: string, retry = false) =>
  `/api/me/books/${bookId}/post/generation?itemKey=${encodeURIComponent(itemKey)}${retry ? '&retry=true' : ''}`

const GENERATION_LABELS: Record<string, string> = {
  planning: '학습 계획 작성 중',
  writing: '글 작성 중',
  ready: '글 작성 중',
  none: '글이 없는 항목입니다',
}

/** What the page shows for a state; null for one that shows its own message, or one it does not know. */
export const generationLabel = (state: string): string | null => GENERATION_LABELS[state] ?? null

/** While the plan or the post is being written the page asks again; any other state is where it stops. */
export const isUnderway = (state: string) => state === 'planning' || state === 'writing'

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
