import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { Prose } from '@/components/Prose'
import { formatDateTime } from '@/lib/accounts'
import type { Book } from '@/lib/books'
import { headingOf, type Place, type Post, placeOf, postHref, postPath } from '@/lib/posts'
import { bookOf, tocInUse } from '@/lib/server-books'
import { serverFind } from '@/lib/server-session'

type Props = PageProps<'/books/[bookId]/post'>

/** The book and the section the address names, or null when either is not the reader's to read. */
async function sectionOf({
  params,
  searchParams,
}: Props): Promise<(Place & { book: Book }) | null> {
  const { item } = await searchParams
  const book = await bookOf((await params).bookId)
  const toc = book && (await tocInUse(book))
  const place = toc && typeof item === 'string' ? placeOf(toc.items, item) : null
  return book && place ? { ...place, book } : null
}

export async function generateMetadata(props: Props): Promise<Metadata> {
  const section = await sectionOf(props)
  return { title: section ? headingOf(section.item) : '글' }
}

export default async function PostPage(props: Props) {
  const section = await sectionOf(props)
  if (!section) notFound()
  const { book, item, previous, next } = section
  const post = await serverFind<Post>(postPath(book.id, item.itemKey))
  return (
    <article className="page">
      <p className="post-origin">
        <Link href={`/books/${book.id}`}>{book.title}</Link>
      </p>
      <h1 className="page-title">{headingOf(item)}</h1>
      {post ? (
        <>
          <p className="post-meta">
            <span className="tag">AI 학습 콘텐츠 · 책 본문 요약 아님</span>
            <span>{formatDateTime(post.generatedAt)} 생성</span>
          </p>
          <Prose markdown={post.contentMd} />
        </>
      ) : (
        <div className="empty">아직 글이 없습니다.</div>
      )}
      <nav className="post-nav" aria-label="앞뒤 항목">
        {previous ? (
          <Link className="post-step" href={postHref(book.id, previous.itemKey)}>
            <span className="field-hint">이전</span>
            {headingOf(previous)}
          </Link>
        ) : (
          <span />
        )}
        {next && (
          <Link className="post-step post-step-next" href={postHref(book.id, next.itemKey)}>
            <span className="field-hint">다음</span>
            {headingOf(next)}
          </Link>
        )}
      </nav>
    </article>
  )
}
