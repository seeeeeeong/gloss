import type { Metadata } from 'next'
import { bookmarksBeside, shelfOf } from '@/features/books/api/server-books'
import { Library } from '@/features/books/views/Library'

export const metadata: Metadata = { title: '내 서재' }

export default async function Shelf() {
  const [books, bookmarks] = await Promise.all([shelfOf(), bookmarksBeside()])
  return <Library books={books} mark={bookmarks[0]} />
}
