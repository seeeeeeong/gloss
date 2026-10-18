import type { Metadata } from 'next'
import { notFound, redirect } from 'next/navigation'
import { bookmarksBeside, bookOf, tocInUse } from '@/features/books/api/server-books'
import { ReaderSwitch } from '@/features/reader/views/ReaderSwitch'
import type { PostSummary } from '@/lib/posts'
import { serverGet } from '@/lib/server-session'

type Props = PageProps<'/books/[bookId]'>

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const book = await bookOf((await params).bookId)
  return { title: book?.title ?? '책' }
}

/** The book to read. Contents are confirmed while a book is added; a book without them goes back there. */
export default async function BookPage({ params, searchParams }: Props) {
  const book = await bookOf((await params).bookId)
  if (!book) notFound()
  const toc = await tocInUse(book)
  if (!toc) redirect('/books/new')
  const [posts, bookmarks, { item }] = await Promise.all([
    serverGet<PostSummary[]>(`/api/me/books/${book.id}/posts`),
    bookmarksBeside(),
    searchParams,
  ])
  const start = toc.items.find((it) => it.itemKey === item)?.itemKey ?? null
  const shown = { id: book.id, title: book.title }
  return (
    // Another book starts the reader over: nothing of the last one, such as its bookmarks, carries across.
    <ReaderSwitch
      key={book.id}
      book={shown}
      items={toc.items}
      posts={posts}
      start={start}
      bookmarks={bookmarks.filter((bookmark) => bookmark.bookId === book.id)}
    />
  )
}
