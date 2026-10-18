import type { Metadata } from 'next'
import { Library } from '@/components/Library'
import { bookmarksBeside, shelfOf } from '@/lib/server-books'

export const metadata: Metadata = { title: '내 서재' }

export default async function Shelf() {
  const [books, bookmarks] = await Promise.all([shelfOf(), bookmarksBeside()])
  return <Library books={books} mark={bookmarks[0]} />
}
