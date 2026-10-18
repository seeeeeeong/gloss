import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { bookmarksBeside, bookOf, tocInUse } from '@/features/books/api/server-books'
import { BookContents } from '@/features/books/views/BookContents'
import { Reader } from '@/features/reader/views/Reader'
import { landingOf } from '@/lib/books'
import type { PostSummary } from '@/lib/posts'
import { serverGet } from '@/lib/server-session'

type Props = PageProps<'/books/[bookId]'>

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const book = await bookOf((await params).bookId)
  return { title: book?.title ?? '책' }
}

/** The book to read once its contents are confirmed; until then, the contents to confirm. */
export default async function BookPage({ params, searchParams }: Props) {
  const book = await bookOf((await params).bookId)
  if (!book) notFound()
  const toc = landingOf(book) === 'reader' ? await tocInUse(book) : null
  if (!toc) return <BookContents book={book} inUse={null} />
  const [posts, bookmarks, { item }] = await Promise.all([
    serverGet<PostSummary[]>(`/api/me/books/${book.id}/posts`),
    bookmarksBeside(),
    searchParams,
  ])
  const start = toc.items.find((it) => it.itemKey === item)?.itemKey ?? null
  const { id, title, authors, publisher, source, sourceUrl, voice } = book
  const shown = {
    id,
    title,
    authors,
    publisher,
    source,
    sourceUrl,
    voice,
    tocVersionNo: toc.versionNo,
  }
  return (
    // Another book starts the reader over: nothing of the last one, such as its bookmarks, carries across.
    <Reader
      key={book.id}
      book={shown}
      items={toc.items}
      posts={posts}
      start={start}
      bookmarks={bookmarks.filter((bookmark) => bookmark.bookId === book.id)}
    />
  )
}
