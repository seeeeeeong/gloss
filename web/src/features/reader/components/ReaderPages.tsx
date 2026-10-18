import Link from 'next/link'
import type { MouseEvent } from 'react'
import { Icon } from '@/components/ui/Icon'
import { SourceCredit } from '@/components/ui/SourceCredit'
import {
  CONTENTS,
  COVER,
  type ContentsRow,
  type ItemView,
  type RowMark,
} from '@/features/reader/utils/reader'
import { type Book, contentsHref } from '@/lib/books'
import { readerHref } from '@/lib/posts'
import { voiceLabel } from '@/lib/styles'
import { Prose } from './Prose'

export type ReaderBook = Pick<
  Book,
  'id' | 'title' | 'authors' | 'publisher' | 'source' | 'sourceUrl' | 'voice'
> & { tocVersionNo: number }

export const KEY_HREF = '/me#api-key-head'
export const USAGE_HREF = '/me#usage'

/** The first page: the book's title, where it came from and how its posts read, centred on the page. */
export function Cover({ book }: { book: ReaderBook }) {
  const voice = voiceLabel(book.voice)
  const facts = [
    book.authors.join(', '),
    book.publisher ?? '',
    voice ? `문체: ${voice}` : '',
  ].filter((fact) => fact.length > 0)
  return (
    <div className="cover" data-unit={COVER}>
      <h1 className="cv-t">{book.title}</h1>
      <p className="cv-f">
        {facts.map((fact) => (
          <span key={fact}>{fact}</span>
        ))}
        <span>
          목차 버전 {book.tocVersionNo} ·{' '}
          <Link className="dl" href={contentsHref(book.id)}>
            고치기
          </Link>
        </span>
        <SourceCredit book={book} />
      </p>
    </div>
  )
}

type ContentsProps = {
  bookId: string
  rows: ContentsRow[]
  markOf: (row: ContentsRow) => RowMark
  onGo: (key: string) => void
}

/** The 차례: every row is a link to its item, opened in place. */
export function Contents({ bookId, rows, markOf, onGo }: ContentsProps) {
  const go = (key: string) => (event: MouseEvent<HTMLAnchorElement>) => {
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
    event.preventDefault()
    onGo(key)
  }
  return (
    <section className="toc-pg" data-unit={CONTENTS} aria-labelledby="contents-title">
      <h1 className="toc-t" id="contents-title">
        차례
      </h1>
      {rows.map((row) => {
        const mark = markOf(row)
        const cls = ['tc-row', `tc-${row.kind}`, (mark.todo || mark.live) && 'todo']
        return (
          <a
            key={row.key}
            className={cls.filter(Boolean).join(' ')}
            style={row.depth > 0 ? { paddingLeft: 14 * (row.depth + 1) } : undefined}
            href={readerHref(bookId, row.key)}
            onClick={go(row.key)}
          >
            <span className="tl">{row.label}</span>
            {mark.live ? (
              <span className="spin" role="img" aria-label="쓰는 중" />
            ) : (
              mark.read && (
                <span className="rd" role="img" aria-label="읽음">
                  <Icon name="check" size={16} />
                </span>
              )
            )}
          </a>
        )
      })}
    </section>
  )
}

type ItemProps = {
  unit: number
  number: string | null
  title: string
  view: ItemView
  markdown: string | null
  onRetry: () => void
}

/** One item of the book: its heading, then its post or how the post stands. */
export function ItemArticle({ unit, number, title, view, markdown, onRetry }: ItemProps) {
  return (
    <article className="item" data-unit={unit} aria-busy={view.kind === 'writing'}>
      {view.kind === 'written' && view.stale && (
        <p className="stale">
          이전 목차로 쓴 글이에요.
          <button type="button" className="tbtn" onClick={onRetry}>
            다시 쓰기
          </button>
        </p>
      )}
      <h1 tabIndex={-1}>
        {/* The space keeps the number apart from the title in copied text and read aloud. */}
        {number && <span className="no">{number} </span>}
        {title}
      </h1>
      <ItemBody view={view} markdown={markdown} onRetry={onRetry} />
    </article>
  )
}

function ItemBody({ view, markdown, onRetry }: Omit<ItemProps, 'unit' | 'number' | 'title'>) {
  const retry = (
    <div className="actions">
      <button type="button" className="btn sec sm" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  )
  switch (view.kind) {
    case 'written':
      return markdown === null ? null : <Prose markdown={markdown} />
    case 'writing':
      return (
        <p>
          <span className="cur" aria-hidden="true" />
          <span className="sr-only">쓰는 중</span>
        </p>
      )
    case 'none':
      return <p className="quiet">글이 없는 항목이에요.</p>
    case 'noKey':
      return (
        <p className="fail">
          글을 쓰려면 DeepSeek API 키가 필요해요.
          <br />
          <Link className="dl" href={KEY_HREF}>
            마이페이지에서 키 등록하기
          </Link>
        </p>
      )
    case 'limit':
      return (
        <>
          <p className="fail">
            이번 달 한도에 닿아서 이 글을 쓰지 못했어요.
            <br />
            <Link className="dl" href={USAGE_HREF}>
              마이페이지에서 한도 바꾸기
            </Link>
          </p>
          {retry}
        </>
      )
    case 'failed':
      return (
        <>
          <p className="fail">
            {view.message}
            {view.fix && <br />}
            {view.fix === 'key' && (
              <Link className="dl" href={KEY_HREF}>
                마이페이지에서 키 등록하기
              </Link>
            )}
            {view.fix === 'usage' && (
              <Link className="dl" href={USAGE_HREF}>
                마이페이지에서 한도 바꾸기
              </Link>
            )}
          </p>
          {retry}
        </>
      )
    case 'todo':
      return null
  }
}
