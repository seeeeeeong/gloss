import type { Metadata } from 'next'
import { bookmarksOf } from '@/features/books/api/server-books'
import { Bookmarks } from '@/features/books/views/Bookmarks'

export const metadata: Metadata = { title: '책갈피' }

export default async function BookmarksPage() {
  const bookmarks = await bookmarksOf()
  return <Bookmarks bookmarks={bookmarks} />
}
