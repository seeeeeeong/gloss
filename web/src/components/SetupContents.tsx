'use client'

import { Icon } from '@/components/Icon'
import { Cover as Jacket } from '@/components/Library'
import { SetupStyle } from '@/components/SetupStyle'
import { creditOf } from '@/lib/books'
import {
  type ChosenStyle,
  contentsIntro,
  factsOf,
  type HeldContents,
  hostOf,
  itemsCount,
  SEEK_LABEL,
} from '@/lib/setup'
import type { Voice } from '@/lib/styles'

/** The live contents' card, where focus goes back when their panel closes. */
export const CONTENTS_CARD = 'contents-card'

/** Contents Gloss brought, as its answer shows them, and what the reader may do with them. */
export type ShownContents = {
  seq: number
  held: HeldContents
  /** The contents a book would be made from now; earlier ones, and those started with, are read only. */
  live: boolean
  /** Their panel is open beside the thread. */
  reviewing: boolean
  /** A message or a pick is on its way: the contents may be about to change, so nothing starts. */
  waiting: boolean
  /** The voice a start is on its way with. */
  starting: Voice | null
  /** Detailed contents are being looked for: these wait for them. */
  searching: boolean
  /** Why the last start or look did not go through, said as Gloss would. */
  notice: string | null
  onReview: () => void
  onSeek: () => void
  onStart: (style: ChosenStyle) => void
}

type Props = { contents: ShownContents; typed: boolean }

/**
 * The contents in Gloss's answer: a line, their card, which opens them whole in the panel, then the
 * question of how the posts are written, whose answer starts with them.
 */
export function SetupContents({ contents, typed }: Props) {
  const { held, live, reviewing, waiting, starting, notice, onReview, onStart } = contents
  return (
    <>
      <p>{contentsIntro(typed, held.found !== null)}</p>
      <ContentsCard
        title={held.title}
        items={held.items.length}
        open={live ? { reviewing, onReview } : undefined}
      />
      <SetupStyle live={live} starting={starting} blocked={waiting} onStart={onStart} />
      {notice && <p role="alert">{notice}</p>}
    </>
  )
}

type CardProps = {
  title: string
  items: number
  /** The live contents open their panel from the card. */
  open?: { reviewing: boolean; onReview: () => void }
}

/** Contents by their title and how many items they have; the live ones are a way into the panel. */
export function ContentsCard({ title, items, open }: CardProps) {
  const text = (
    <>
      <Icon name="list" />
      <span className="tcard-text">
        <span className="tcard-title">{title}</span>
        <span className="tcard-meta">{itemsCount(items)}</span>
      </span>
    </>
  )
  if (!open) return <div className="tcard">{text}</div>
  return (
    <button
      type="button"
      id={CONTENTS_CARD}
      className="tcard"
      aria-expanded={open.reviewing}
      onClick={open.onReview}
    >
      {text}
      <Icon name="arrowR" size={18} />
    </button>
  )
}

type PanelProps = {
  contents: ShownContents
  onClose: () => void
  /** Back to the thread to say more, the panel closed. */
  onTalk: () => void
  /** Back to the thread to pick how the posts are written, which starts with these contents. */
  onChoose: () => void
}

/** 목차 확인 beside the thread: the book picked, where its contents came from and every item. */
export function ContentsPanel({ contents, onClose, onTalk, onChoose }: PanelProps) {
  const { held } = contents
  return (
    <>
      <div className="p-head">
        <h2 className="p-title" id="panel-title">
          목차 확인
        </h2>
        <button
          type="button"
          className="ibtn close"
          aria-label="닫기"
          data-tip="닫기"
          onClick={onClose}
        >
          <Icon name="x" />
        </button>
      </div>
      <div className="p-body">
        <BookHeader contents={contents} />
        <h3 className="p-sub">
          목차<span>{held.items.length}</span>
        </h3>
        <ol className="p-items">
          {held.items.map((item) => (
            <li key={item.ord} className={`d${Math.min(item.depth, 3)}`}>
              {item.number && <span className="no">{item.number}</span>}
              <span>{item.title}</span>
            </li>
          ))}
        </ol>
      </div>
      <div className="p-foot">
        <div className="actions">
          <button type="button" className="btn sec" onClick={onTalk}>
            대화 더 이어가기
          </button>
          <button type="button" className="btn primary" onClick={onChoose}>
            이 목차로 시작
            <Icon name="arrowR" size={16} />
          </button>
        </div>
      </div>
    </>
  )
}

/** The book picked: its cover, the title linked to its page, the facts, then the sources. */
function BookHeader({ contents }: { contents: ShownContents }) {
  const { held } = contents
  const href = held.book?.url ?? held.sourceUrl
  return (
    <div className="p-book">
      <div className="p-cover">
        {held.book?.cover ? (
          <Cover src={held.book.cover} />
        ) : (
          <Jacket book={{ id: held.ref ?? held.title, title: held.title, authors: held.authors }} />
        )}
      </div>
      <div className="p-book-body">
        <span className="p-label">선택한 책</span>
        <p className="p-book-title">
          {href ? (
            <a className="dl" href={href} target="_blank" rel="noopener noreferrer">
              {held.title}
            </a>
          ) : (
            held.title
          )}
        </p>
        <ul className="facts">
          {factsOf(held).map((fact) => (
            <li key={fact}>{fact}</li>
          ))}
        </ul>
        <SourceRow contents={contents} />
      </div>
    </div>
  )
}

/**
 * Where the contents came from: the page they were found on, as a recommendation cites one, then the
 * bookshop whose details the header shows, as its terms ask, and a look on another source.
 */
function SourceRow({ contents }: { contents: ShownContents }) {
  const { held, live, waiting, starting, searching, onSeek } = contents
  const credit = held.source && held.sourceUrl ? creditOf(held.source) : null
  const found = held.found
  const busy = starting !== null || searching
  if (!credit && !found && !live) return null
  return (
    <p className="bk-src">
      {found && (
        <>
          <a
            className="dl found"
            href={found.url}
            target="_blank"
            rel="noopener noreferrer"
            title={found.title}
          >
            <span>{found.title}</span>
            <Icon name="upRight" size={14} />
          </a>
          <a className="spill" href={found.url} target="_blank" rel="noopener noreferrer">
            {hostOf(found.url)}
          </a>
        </>
      )}
      {credit && held.sourceUrl && (
        <a className="dl" href={held.sourceUrl} target="_blank" rel="noopener noreferrer">
          <span>{credit}</span>
          <Icon name="upRight" size={14} />
        </a>
      )}
      {live && held.ref !== null && (
        <span className="tacts">
          <button
            type="button"
            className="tbtn"
            aria-disabled={busy || waiting}
            aria-busy={searching}
            onClick={() => {
              if (!busy && !waiting) onSeek()
            }}
          >
            {SEEK_LABEL}
          </button>
        </span>
      )}
    </p>
  )
}

// Straight from the source's server: Next's optimizer would fetch and keep a copy of every cover.
export function Cover({ src }: { src: string }) {
  // biome-ignore lint/performance/noImgElement: see above
  return <img src={src} alt="" loading="lazy" referrerPolicy="no-referrer" />
}
