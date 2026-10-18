import type { Metadata } from 'next'
import { Bookmarks } from '@/components/Bookmarks'
import { bookmarksOf } from '@/lib/server-books'

export const metadata: Metadata = { title: '책갈피' }

export default async function BookmarksPage() {
  const bookmarks = await bookmarksOf()
  return <Bookmarks bookmarks={bookmarks} />
}
