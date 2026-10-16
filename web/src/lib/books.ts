/** A book on the shelf, as GET /api/me/books lists it. */
export type BookSummary = {
  id: string
  title: string
  authors: string[]
  publisher: string | null
  isbn13: string | null
  source: string
  sourceUrl: string | null
  confirmed: boolean
  createdAt: string
}

export type TocItem = {
  id: string
  parentId: string | null
  depth: number
  number: string | null
  title: string
  itemKey: string
}

export type Toc = {
  id: string
  versionNo: number
  origin: string
  status: string
  confirmedAt: string | null
  raw: string
  items: TocItem[]
}

/** A book with its latest contents, which may be a draft newer than the one in use. */
export type Book = {
  id: string
  title: string
  authors: string[]
  publisher: string | null
  isbn13: string | null
  source: string
  sourceUrl: string | null
  currentTocId: string | null
  toc: Toc
}

/** An edition a search found. `ref` adds it; `url` is its page at the source. */
export type BookCandidate = {
  ref: string
  source: string
  url: string
  title: string
  authors: string[]
  publisher: string | null
  isbn13: string
  publishedOn: string | null
  kind: string | null
  hasContents: boolean
}

export type TocNode = TocItem & { children: TocNode[] }

const BOOK_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** The server answers a path that is no id with an error, not a 404, so it is never asked. */
export const isBookId = (value: string) => BOOK_ID.test(value)

/** Authors typed into one field, separated by commas. */
export function authorsOf(raw: string): string[] {
  return raw
    .split(/[,，、]/)
    .map((name) => name.trim())
    .filter((name) => name.length > 0)
}

export function bylineOf(book: Pick<BookSummary, 'authors' | 'publisher'>): string {
  return [book.authors.join(', '), book.publisher ?? '']
    .filter((part) => part.length > 0)
    .join(' · ')
}

const SOURCES: Record<string, string> = { yes24: 'YES24' }

/** Null for a book the reader typed in: there is no one to credit. The source's terms ask for it. */
export function creditOf(source: string): string | null {
  const name = SOURCES[source]
  return name ? `출처 ${name}` : null
}

/** What tells one edition from another: who, which publisher, what year, what kind. */
export function editionOf(
  candidate: Pick<BookCandidate, 'authors' | 'publisher' | 'publishedOn' | 'kind'>,
): string {
  return [bylineOf(candidate), candidate.publishedOn?.slice(0, 4) ?? '', candidate.kind ?? '']
    .filter((part) => part.length > 0)
    .join(' · ')
}

export const searchPath = (query: string) =>
  `/api/me/books/search?query=${encodeURIComponent(query)}`

/** Items arrive in reading order, so each child lands after its siblings before it. */
export function treeOf(items: TocItem[]): TocNode[] {
  const nodes = items.map((item): TocNode => ({ ...item, children: [] }))
  const byId = new Map(nodes.map((node) => [node.id, node]))
  const roots: TocNode[] = []
  for (const node of nodes) {
    const parent = node.parentId === null ? undefined : byId.get(node.parentId)
    if (parent) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}

const TOC_STATUSES: Record<string, string> = { draft: '확정 전', confirmed: '확정' }

export const tocStatusLabel = (status: string) => TOC_STATUSES[status] ?? status

export const contentsHref = (bookId: string) => `/books/${bookId}/contents`

/** A book opens in the reader once its contents are confirmed, and at their review until then. */
export const landingOf = (book: Pick<Book, 'currentTocId'>): 'reader' | 'review' =>
  book.currentTocId === null ? 'review' : 'reader'

/** A section of the contents page: one version, and whether it can be edited and confirmed there. */
export type ContentsSection = { id: 'toc' | 'draft'; title: string; toc: Toc; review: boolean }

/**
 * The version in use, then a newer draft when the latest differs from it. Only the latest is
 * edited or confirmed; with no newer draft, that is the version in use.
 */
export function contentsSectionsOf(inUse: Toc | null, latest: Toc): ContentsSection[] {
  if (!inUse) return [{ id: 'draft', title: '목차', toc: latest, review: true }]
  const current: ContentsSection = {
    id: 'toc',
    title: '목차',
    toc: inUse,
    review: inUse.id === latest.id,
  }
  if (inUse.id === latest.id) return [current]
  return [current, { id: 'draft', title: '새 목차', toc: latest, review: true }]
}
