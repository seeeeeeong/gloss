import Link from 'next/link'
import type { ItemView } from '@/features/reader/utils/reader'
import type { Book, SourceLink } from '@/lib/books'
import { Prose } from './Prose'

/**
 * [setupHref] is the conversation the book was chosen in, when it was chosen in one. The edition's
 * details and its source's page are shown in the contents' head, where present.
 */
export type ReaderBook = Pick<Book, 'id' | 'title'> &
  Partial<Pick<Book, 'publisher' | 'publishedOn' | 'kind' | 'source' | 'sourceUrl'>> & {
    setupHref?: string | null
  }

export const KEY_HREF = '/me#api-key-head'
export const USAGE_HREF = '/me#usage'

type ItemProps = {
  unit: number
  number: string | null
  title: string
  view: ItemView
  markdown: string | null
  /** Where the contents came from, credited on every post. */
  credit: SourceLink | null
  onRetry: () => void
}

/** One item of the book: its heading, then its post or how the post stands. */
export function ItemArticle({ unit, number, title, view, markdown, credit, onRetry }: ItemProps) {
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
      {view.kind === 'written' && markdown !== null && <Byline credit={credit} />}
      <ItemBody view={view} markdown={markdown} onRetry={onRetry} />
    </article>
  )
}

/** Says a model wrote the post, so it is never taken for the book's text, and credits the contents' source. */
function Byline({ credit }: { credit: SourceLink | null }) {
  return (
    <p className="byline">
      AI 학습 콘텐츠 · 책 본문 요약 아님
      {credit && (
        <>
          {' · 목차 출처 '}
          <a className="dl" href={credit.url} target="_blank" rel="noopener noreferrer">
            {credit.name}
          </a>
        </>
      )}
    </p>
  )
}

function ItemBody({ view, markdown, onRetry }: Pick<ItemProps, 'view' | 'markdown' | 'onRetry'>) {
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
