'use client'

import Link from 'next/link'
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react'
import { load } from '../lib/api'
import { creditOf, editionOf } from '../lib/books'
import {
  booksPath,
  type CitedPage,
  canPick,
  cardItems,
  failureLink,
  hostOf,
  latestBooks,
  listOf,
  liveContents,
  type OfferedBook,
  type RecommendedBook,
  type SetupMessage,
  type SetupSession,
  type SetupState,
  type TurnStep,
  turnOf,
} from '../lib/setup'
import { Icon } from './Icon'
import { ListFailed } from './ListFailed'
import { useDismiss } from './useDismiss'

type TurnProps = {
  message: SetupMessage
  session: SetupSession
  busy: boolean
  panel: boolean
  onPick: (book: OfferedBook) => void
  onType: (book: OfferedBook) => void
  onPanel: (open: boolean) => void
}

/**
 * What Gloss says: the model's answer (recommended books, the editions of a book named, or words) after
 * the steps it took, what stopped it, the contents brought, or done.
 */
export function GlossTurn({ message, session, busy, panel, onPick, onType, onPanel }: TurnProps) {
  const turn = turnOf(session.messages, message.seq)
  const think = turn && <ThinkLine steps={turn.steps} seconds={turn.seconds} />
  const list = listOf(session.messages, message)
  const lists = {
    sessionId: session.id,
    latest: list !== null && latestBooks(session) === list,
    state: session.state,
    busy,
    onPick,
    onType,
  }
  switch (message.kind) {
    case 'books':
      // A search the model ran shows as a step of its answer.
      if (message.payload.callId) return null
      if (message.payload.books.length === 0) return <p>찾은 책이 없어요.</p>
      return <FoundBooks seq={message.seq} {...lists} />
    case 'books_shown':
      return (
        <>
          {think}
          {list !== null && <FoundBooks seq={list} {...lists} />}
        </>
      )
    case 'recommend':
      return (
        <>
          {think}
          <p>{message.payload.intro}</p>
          <Recommendation seq={message.seq} picks={message.payload.books} {...lists} />
        </>
      )
    case 'said':
      return (
        <>
          {think}
          {paragraphsOf(message.payload.text)}
        </>
      )
    case 'search_failed': {
      const link = failureLink(message.payload.code)
      return (
        <>
          {think}
          <p>
            {message.payload.message}
            {link && (
              <>
                {' '}
                <Link className="dl" href={link.href}>
                  {link.label}
                </Link>
              </>
            )}
          </p>
        </>
      )
    }
    case 'contents':
      return (
        <>
          <p>
            {message.payload.typed ? '목차를 받았어요.' : '목차를 가져왔어요.'} 살펴보시고 괜찮으면
            이 목차로 시작해 주세요.
          </p>
          <ContentsCard
            title={message.payload.title}
            items={cardItems(session, message.seq, message.payload.items)}
            live={liveContents(session) === message.seq}
            open={panel}
            onPanel={onPanel}
          />
        </>
      )
    case 'done':
      return (
        <>
          <p>준비됐어요. 항목을 열면 그 글을 바로 써 드릴게요.</p>
          <Link className="submit" href={`/books/${message.payload.bookId}`}>
            책으로 이동
          </Link>
        </>
      )
    default:
      return null
  }
}

// What the model wrote, as plain paragraphs: never markup.
function paragraphsOf(text: string) {
  const lines = text.split(/\n+/).filter((line) => line.trim() !== '')
  let at = 0
  return lines.map((line) => {
    at += line.length + 1
    return <p key={at}>{line}</p>
  })
}

/** How long the model took, opening to the searches it ran. */
function ThinkLine({ steps, seconds }: { steps: TurnStep[]; seconds: number }) {
  const [open, setOpen] = useState(false)
  const label = `${seconds}초 동안 처리함`
  if (steps.length === 0) return <p className="think">{label}</p>
  return (
    <div className="think">
      <button
        type="button"
        className="think-head"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        {label}
        <Icon name="chevR" size={16} />
      </button>
      {open && (
        <ul className="step-list">
          {steps.map((step) => (
            <li key={step.seq}>
              <Icon name={step.kind === 'search' ? 'globe' : 'book'} size={16} />
              <span>
                <b>{step.kind === 'search' ? '검색' : 'YES24'}</b>
                {step.query} · {step.kind === 'search' ? `${step.found}곳` : `판본 ${step.found}개`}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

type CardProps = {
  title: string
  items: number
  /** Only the latest contents brought, while a book is chosen, open the panel; earlier ones are history. */
  live: boolean
  open: boolean
  onPanel: (open: boolean) => void
}

function ContentsCard({ title, items, live, open, onPanel }: CardProps) {
  const body = (
    <span className="tcard-text">
      <span className="tcard-title">{title}</span>
      <span className="tcard-meta">목차 · 항목 {items}개</span>
    </span>
  )
  if (!live) return <div className="tcard">{body}</div>
  return (
    <button type="button" className="tcard" aria-expanded={open} onClick={() => onPanel(!open)}>
      {body}
      <Icon name="chevR" />
    </button>
  )
}

type ListProps = {
  sessionId: string
  seq: number
  /** Only the latest list is asked for at once; an earlier one when the reader opens it. */
  latest: boolean
  state: SetupState
  busy: boolean
  onPick: (book: OfferedBook) => void
  onType: (book: OfferedBook) => void
}

/** The editions a search found, every one, to pick from. */
function FoundBooks(props: ListProps) {
  return (
    <Listed sessionId={props.sessionId} seq={props.seq} latest={props.latest}>
      {(books) => (
        <>
          <div className="books">
            {books.map((book) => (
              <BookRow key={book.ref} book={book} {...props} />
            ))}
          </div>
          <Credit books={books} />
          <p>어떤 책으로 학습할까요?</p>
        </>
      )}
    </Listed>
  )
}

/** The editions the model recommended, best first, each with its reason and the pages it cited. */
function Recommendation({ picks, ...props }: ListProps & { picks: RecommendedBook[] }) {
  return (
    <Listed sessionId={props.sessionId} seq={props.seq} latest={props.latest}>
      {(books) => {
        const shown = picks.flatMap((pick) => {
          const book = books.find((found) => found.ref === pick.ref)
          return book ? [{ pick, book }] : []
        })
        return (
          <>
            <div className="books">
              {shown.map(({ pick, book }, i) => (
                <BookRow
                  key={book.ref}
                  book={book}
                  rank={i + 1}
                  reason={pick.reason}
                  sources={pick.sources}
                  {...props}
                />
              ))}
            </div>
            <Credit books={books} />
            <p>어떤 책으로 학습할까요?</p>
          </>
        )
      }}
    </Listed>
  )
}

function Credit({ books }: { books: OfferedBook[] }) {
  const credit = creditOf(books[0]?.source ?? '')
  return credit ? <p className="credit">{credit}</p> : null
}

type ListedProps = {
  sessionId: string
  seq: number
  latest: boolean
  children: (books: OfferedBook[]) => ReactNode
}

/**
 * A list's editions, asked for again as it is shown: the session keeps only their refs, so the
 * source's own details are never stored.
 */
function Listed(props: ListedProps) {
  // A retry starts the list over, open, as the reader asked for it again.
  const [tries, setTries] = useState(0)
  return (
    <ListedOnce
      key={tries}
      {...props}
      latest={props.latest || tries > 0}
      onRetry={() => setTries(tries + 1)}
    />
  )
}

function ListedOnce({
  sessionId,
  seq,
  latest,
  children,
  onRetry,
}: ListedProps & { onRetry: () => void }) {
  const [books, setBooks] = useState<OfferedBook[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(latest)

  useEffect(() => {
    if (!open) return
    let live = true
    async function ask() {
      const result = await load<OfferedBook[]>(booksPath(sessionId, seq))
      if (!live) return
      if (result.ok) setBooks(result.data)
      else setError(result.message)
    }
    void ask()
    return () => {
      live = false
    }
  }, [sessionId, seq, open])

  if (!open)
    return (
      <button type="button" className="tbtn" onClick={() => setOpen(true)}>
        결과 보기
      </button>
    )
  if (error) return <ListFailed message={error} onRetry={onRetry} />
  if (!books) return <p className="think pulse">불러오는 중</p>
  return children(books)
}

type RowProps = {
  book: OfferedBook
  state: SetupState
  busy: boolean
  onPick: (book: OfferedBook) => void
  onType: (book: OfferedBook) => void
  /** A recommendation's place, its reason and the pages it cited. */
  rank?: number
  reason?: string
  sources?: CitedPage[]
}

export function BookRow({ book, state, busy, onPick, onType, rank, reason, sources }: RowProps) {
  const meta = [editionOf(book), book.hasContents ? '' : '목차 없음'].filter(Boolean).join(' · ')
  const pickable = canPick(state, book)
  const typable = state !== 'confirmed' && !book.hasContents
  return (
    <article className="bk">
      <div className="bk-in">
        {book.cover && (
          <div className="bk-cover">
            <Cover src={book.cover} />
          </div>
        )}
        <div className="bk-body">
          <div className="bk-title">
            <p>
              {rank !== undefined && `${rank}. `}
              <a className="dl" href={book.url} target="_blank" rel="noopener noreferrer">
                {book.title}
              </a>
            </p>
            {pickable && (
              <button
                type="button"
                className="pick"
                disabled={busy}
                aria-label={`${book.title}, 이 책으로 학습`}
                onClick={() => onPick(book)}
              >
                이 책으로 학습
              </button>
            )}
            {typable && (
              <button
                type="button"
                className="pick"
                disabled={busy}
                aria-label={`${book.title}, 직접 입력`}
                onClick={() => onType(book)}
              >
                직접 입력
              </button>
            )}
          </div>
          <p className="bk-meta">{meta}</p>
          {reason && <p className="bk-why">{reason}</p>}
          {sources && sources.length > 0 && <Sources pages={sources} />}
        </div>
      </div>
    </article>
  )
}

/**
 * The pages a recommendation cited, as the prototype's source row: the first by its title, then one
 * pill with its host and how many more, which opens the rest, so every page cited can be opened.
 */
function Sources({ pages }: { pages: CitedPage[] }) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLSpanElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const close = useCallback(() => setOpen(false), [])
  useDismiss(open, root, button, close)
  const [first, ...rest] = pages
  if (!first) return null
  return (
    <p className="bk-src">
      <a className="dl" href={first.url} target="_blank" rel="noopener noreferrer">
        <span>{first.title}</span>
        <Icon name="upRight" size={14} />
      </a>
      {rest.length === 0 ? (
        <a className="spill" href={first.url} target="_blank" rel="noopener noreferrer">
          {hostOf(first.url)}
        </a>
      ) : (
        <span className="src-more" ref={root}>
          <button
            ref={button}
            type="button"
            className="spill"
            aria-expanded={open}
            aria-label={`출처 ${pages.length}곳: ${pages.map((page) => hostOf(page.url)).join(', ')}`}
            onClick={() => setOpen(!open)}
          >
            {hostOf(first.url)} +{rest.length}
          </button>
          {open && <MorePages pages={rest} />}
        </span>
      )}
    </p>
  )
}

/** The cited pages past the first, each a link, under the pill that opened them. */
export function MorePages({ pages }: { pages: CitedPage[] }) {
  return (
    <span className="menu src-list">
      {pages.map((page) => (
        <a key={page.url} className="mi" href={page.url} target="_blank" rel="noopener noreferrer">
          <span className="src-title">{page.title}</span>
          <span className="src-host">{hostOf(page.url)}</span>
        </a>
      ))}
    </span>
  )
}

// Straight from the source's server: Next's optimizer would fetch and keep a copy of every cover.
function Cover({ src }: { src: string }) {
  // biome-ignore lint/performance/noImgElement: see above
  return <img src={src} alt="" loading="lazy" referrerPolicy="no-referrer" />
}
