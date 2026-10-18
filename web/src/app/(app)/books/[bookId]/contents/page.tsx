import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { bookOf, tocInUse } from '@/features/books/api/server-books'
import { BookContents } from '@/features/books/views/BookContents'

type Props = PageProps<'/books/[bookId]/contents'>

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const book = await bookOf((await params).bookId)
  return { title: book ? `${book.title} 목차` : '목차' }
}

export default async function ContentsPage({ params }: Props) {
  const book = await bookOf((await params).bookId)
  if (!book) notFound()
  return <BookContents book={book} inUse={await tocInUse(book)} />
}
