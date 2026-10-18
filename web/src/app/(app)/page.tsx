import type { Metadata } from 'next'
import { Library } from '@/components/Library'
import { shelfOf } from '@/lib/server-books'

export const metadata: Metadata = { title: '내 서재' }

export default async function Shelf() {
  const books = await shelfOf()
  return <Library books={books} />
}
