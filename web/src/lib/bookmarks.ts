/** A phrase a reader kept from an item, or the item itself, as GET /api/me/bookmarks lists it. */
export type Bookmark = {
  id: string
  bookId: string
  bookTitle: string
  itemKey: string
  itemNumber: string | null
  itemTitle: string
  /** The phrase kept; null for a bookmark of the item itself. */
  quote: string | null
  createdAt: string
}

export const BOOKMARKS_PATH = '/api/me/bookmarks'

export const bookmarkPath = (id: string) => `${BOOKMARKS_PATH}/${id}`

/** The bookmark of the item itself, without a phrase, if the reader keeps one. */
export const itemMarkOf = (bookmarks: Bookmark[], itemKey: string | null) =>
  itemKey === null
    ? undefined
    : bookmarks.find((bookmark) => bookmark.itemKey === itemKey && bookmark.quote === null)

/** What a bookmark's card shows large: the phrase kept, or the item's own title. */
export const markText = (bookmark: Pick<Bookmark, 'quote' | 'itemNumber' | 'itemTitle'>) =>
  bookmark.quote ?? [bookmark.itemNumber, bookmark.itemTitle].filter(Boolean).join(' ')
