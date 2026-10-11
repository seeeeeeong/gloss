/** A book on the shelf, as GET /api/me/books lists it. */
export type BookSummary = {
  id: string
  title: string
  authors: string[]
  publisher: string | null
  isbn13: string | null
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
  currentTocId: string | null
  toc: Toc
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
