/** A book on the shelf, as GET /api/me/books lists it. */
export type BookSummary = {
  id: string
  title: string
  authors: string[]
  publisher: string | null
  isbn13: string | null
  /** When it came out and what the source sells it as (도서, eBook); null for a book typed in. */
  publishedOn: string | null
  kind: string | null
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
  publishedOn: string | null
  kind: string | null
  source: string
  sourceUrl: string | null
  currentTocId: string | null
  /** How the book's posts are written: the codes of lib/styles. */
  level: string
  voice: string
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

/** The reader's book; a DELETE takes it off their shelf, and adding it again puts it back. */
export const bookPath = (bookId: string) => `/api/me/books/${bookId}`

/** Authors typed into one field, separated by commas. */
export function authorsOf(raw: string): string[] {
  return raw
    .split(/[,，、]/)
    .map((name) => name.trim())
    .filter((name) => name.length > 0)
}

/** YES24 lists several authors as one name, "A,B 공저": each comma gets its space. */
export const authorsLine = (authors: string[]) =>
  authors.map((name) => name.replace(/\s*,\s*/g, ', ')).join(', ')

export function bylineOf(book: Pick<BookSummary, 'authors' | 'publisher'>): string {
  return [authorsLine(book.authors), book.publisher ?? '']
    .filter((part) => part.length > 0)
    .join(' · ')
}

const SOURCES: Record<string, string> = { yes24: 'YES24' }

/** The source's name, null for a book the reader typed in. */
export const sourceNameOf = (source: string): string | null => SOURCES[source] ?? null

/** Null for a book the reader typed in: there is no one to credit. The source's terms ask for it. */
export function creditOf(source: string): string | null {
  const name = sourceNameOf(source)
  return name ? `출처 ${name}` : null
}

export type SourceLink = { name: string; url: string }

/** The source named and its page, null for a book typed in or one without a page. */
export function sourceLinkOf(book: {
  source?: string
  sourceUrl?: string | null
}): SourceLink | null {
  const name = book.source ? sourceNameOf(book.source) : null
  return name && book.sourceUrl ? { name, url: book.sourceUrl } : null
}

/** What tells one edition from another: who, which publisher, what year, and what kind if [withKind]. */
export function editionOf(
  candidate: Pick<BookCandidate, 'authors' | 'publisher' | 'publishedOn' | 'kind'>,
  withKind = true,
): string {
  const kind = withKind ? (candidate.kind ?? '') : ''
  return [bylineOf(candidate), candidate.publishedOn?.slice(0, 4) ?? '', kind]
    .filter((part) => part.length > 0)
    .join(' · ')
}

// A printed book in Korean is what a reader expects; any other kind is worth naming.
const PLAIN_KIND = '도서'

/** What tells a book on the shelf from another edition of it: its publisher, its year, an unusual kind. */
export function editionParts(
  book: Pick<BookSummary, 'publisher' | 'publishedOn' | 'kind'>,
): string[] {
  const kind = book.kind === PLAIN_KIND ? null : book.kind
  return [book.publisher, book.publishedOn?.slice(0, 4) ?? null, kind].filter(
    (part): part is string => part !== null && part.length > 0,
  )
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
