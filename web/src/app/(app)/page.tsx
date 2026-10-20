import type { Metadata } from 'next'
import {
  bookmarksBeside,
  bookOf,
  postCountsBeside,
  recentBeside,
  shelfOf,
  tocInUse,
} from '@/features/books/api/server-books'
import { Library } from '@/features/books/views/Library'
import { readableItems } from '@/features/reader/utils/reader'
import type { RecentBook } from '@/lib/reads'
import type { RecentRead } from '@/lib/shelf'

export const metadata: Metadata = { title: '내 서재' }

export default async function Shelf() {
  const [books, bookmarks, recent, posts] = await Promise.all([
    shelfOf(),
    bookmarksBeside(),
    recentBeside(),
    postCountsBeside(),
  ])
  const latest = await placeOf(recent.books[0])
  return (
    <Library
      books={books}
      recent={recent.books.map((read) => read.bookId)}
      latest={latest}
      mark={bookmarks[0]}
      posts={posts}
    />
  )
}

/** Where the item last read sits among the items its book is read in; none once they leave it out. */
async function placeOf(read: RecentBook | undefined): Promise<RecentRead | undefined> {
  const book = read && (await bookOf(read.bookId))
  const toc = book && (await tocInUse(book))
  if (!read || !toc) return undefined
  const items = readableItems(toc.items)
  const at = items.findIndex((item) => item.itemKey === read.itemKey)
  const item = items[at]
  if (!item) return undefined
  return {
    bookId: read.bookId,
    itemKey: item.itemKey,
    title: item.title,
    position: at + 1,
    total: items.length,
  }
}
