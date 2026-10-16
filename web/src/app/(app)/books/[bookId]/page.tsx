import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { BookContents } from '@/components/BookContents'
import { Reader } from '@/components/Reader'
import { landingOf } from '@/lib/books'
import type { PostSummary } from '@/lib/posts'
import { bookOf, tocInUse } from '@/lib/server-books'
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
  const [posts, { item }] = await Promise.all([
    serverGet<PostSummary[]>(`/api/me/books/${book.id}/posts`),
    searchParams,
  ])
  const start = toc.items.find((it) => it.itemKey === item)?.itemKey ?? null
  const { id, title, authors, publisher, source, sourceUrl } = book
  const shown = { id, title, authors, publisher, source, sourceUrl, tocVersionNo: toc.versionNo }
  return <Reader book={shown} items={toc.items} posts={posts} start={start} />
}
