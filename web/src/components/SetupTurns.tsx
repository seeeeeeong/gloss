'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { Icon } from '@/components/Icon'
import { ListFailed } from '@/components/ListFailed'
import { load } from '@/lib/api'
import { creditOf, editionOf } from '@/lib/books'
import {
  booksPath,
  canPick,
  cardItems,
  latestBooks,
  liveContents,
  type OfferedBook,
  type SetupMessage,
  type SetupSession,
  type SetupState,
} from '@/lib/setup'

type TurnProps = {
  message: SetupMessage
  session: SetupSession
  busy: boolean
  panel: boolean
  onPick: (book: OfferedBook) => void
  onType: (book: OfferedBook) => void
  onPanel: (open: boolean) => void
}

/** What Gloss says: the editions a search found, a failed search, the contents brought, or done. */
export function GlossTurn({ message, session, busy, panel, onPick, onType, onPanel }: TurnProps) {
  switch (message.kind) {
    case 'books':
      if (message.payload.books.length === 0)
        return <p>아직은 책 제목으로만 찾을 수 있어요. 공부하려는 책 제목을 적어 주세요.</p>
      return (
        <FoundBooks
          sessionId={session.id}
          seq={message.seq}
          latest={latestBooks(session) === message.seq}
          state={session.state}
          busy={busy}
          onPick={onPick}
          onType={onType}
        />
      )
    case 'search_failed':
      return <p>{message.payload.message}</p>
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
    <>
      <span className="tcard-text">
        <span className="tcard-title">{title}</span>
        <span className="tcard-meta">목차 · 항목 {items}개</span>
      </span>
    </>
  )
  if (!live) return <div className="tcard">{body}</div>
  return (
    <button type="button" className="tcard" aria-expanded={open} onClick={() => onPanel(!open)}>
      {body}
      <Icon name="chevR" />
    </button>
  )
}

type FoundProps = {
  sessionId: string
  seq: number
  /** Only the latest list is asked for at once; an earlier one when the reader opens it. */
  latest: boolean
  state: SetupState
  busy: boolean
  onPick: (book: OfferedBook) => void
  onType: (book: OfferedBook) => void
}

/**
 * The editions a search found, asked for again as the list is shown: the session keeps only their
 * refs, so the source's own details are never stored.
 */
function FoundBooks(props: FoundProps) {
  // A retry starts the list over, open, as the reader asked for it again.
  const [tries, setTries] = useState(0)
  return (
    <FoundList
      key={tries}
      {...props}
      latest={props.latest || tries > 0}
      onRetry={() => setTries(tries + 1)}
    />
  )
}

function FoundList({
  sessionId,
  seq,
  latest,
  state,
  busy,
  onPick,
  onType,
  onRetry,
}: FoundProps & { onRetry: () => void }) {
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
  const credit = creditOf(books[0]?.source ?? '')
  return (
    <>
      <div className="books">
        {books.map((book) => (
          <BookRow
            key={book.ref}
            book={book}
            pickable={canPick(state, book)}
            typable={state !== 'confirmed' && !book.hasContents}
            busy={busy}
            onPick={onPick}
            onType={onType}
          />
        ))}
      </div>
      {credit && <p className="credit">{credit}</p>}
      <p>어떤 책으로 학습할까요?</p>
    </>
  )
}

type RowProps = {
  book: OfferedBook
  pickable: boolean
  typable: boolean
  busy: boolean
  onPick: (book: OfferedBook) => void
  onType: (book: OfferedBook) => void
}

function BookRow({ book, pickable, typable, busy, onPick, onType }: RowProps) {
  const meta = [editionOf(book), book.hasContents ? '' : '목차 없음'].filter(Boolean).join(' · ')
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
        </div>
      </div>
    </article>
  )
}

// Straight from the source's server: Next's optimizer would fetch and keep a copy of every cover.
function Cover({ src }: { src: string }) {
  // biome-ignore lint/performance/noImgElement: see above
  return <img src={src} alt="" loading="lazy" referrerPolicy="no-referrer" />
}
