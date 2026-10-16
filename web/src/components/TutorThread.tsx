'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import type { TocItem } from '../lib/books'
import { type ChatMessage, noteOf, type ReplyNote, type ThreadRow, threadRowsOf } from '../lib/chat'
import { COPIED_FOR } from '../lib/copied'
import { type LiveTurn, type Refused, shownOf } from '../lib/tutor'
import { Icon } from './Icon'
import { KEY_HREF, USAGE_HREF } from './ReaderPages'

type Props = {
  items: TocItem[]
  messages: ChatMessage[]
  live: LiveTurn | null
  refused: Refused | null
  loadFailed: boolean
  /** Another tab's turn is being answered: the reader's message waiting at the end is not lost. */
  elsewhere?: boolean
  onGo: (itemKey: string) => void
  onReload: () => void
}

/**
 * The conversation: the reader's turns as bubbles, Gloss's as plain text, and a divider wherever a
 * turn comes from another item, which jumps there. The turn being answered ends in the dot cursor.
 */
export function TutorThread(props: Props) {
  const { items, messages, live, refused, loadFailed, elsewhere = false, onGo, onReload } = props
  const shown = shownOf(messages, live)
  const rows = threadRowsOf(shown, items)
  return (
    <div className="t-thread">
      {rows.map((row, i) => {
        if (row.kind === 'divider') {
          return (
            <button
              key={row.key}
              type="button"
              className="t-div"
              disabled={!row.known}
              onClick={() => onGo(row.itemKey)}
            >
              <span>{row.label}</span>
            </button>
          )
        }
        const { message } = row
        if (message.role === 'gloss') return <GlossTurn key={row.key} message={message} />
        const status = waitStatus({ rows, at: i, live, elsewhere })
        return <ReaderTurn key={row.key} message={message} status={status} />
      })}
      {live && (
        <div className="t-turn gloss" aria-busy="true">
          <p>
            {live.text}
            <span className="cur" aria-hidden="true" />
          </p>
        </div>
      )}
      {refused && (
        <div className="t-turn gloss" role="alert">
          <Note note={noteOf({ status: 'failed', code: refused.code, message: refused.message })} />
        </div>
      )}
      {loadFailed && (
        <div className="t-turn gloss">
          <p className="t-note">대화를 불러오지 못했어요.</p>
          <button type="button" className="btn sec sm t-retry" onClick={onReload}>
            다시 시도
          </button>
        </div>
      )}
    </div>
  )
}

type WaitArgs = { rows: ThreadRow[]; at: number; live: LiveTurn | null; elsewhere: boolean }

/** How a reader's turn with no reply under it stands: lost, or being answered in another tab. */
function waitStatus({ rows, at, live, elsewhere }: WaitArgs): ReaderTurnProps['status'] {
  const row = rows[at]
  const next = rows[at + 1]
  const answered = next?.kind === 'message' && next.message.role === 'gloss'
  const waiting = live !== null && row?.kind === 'message' && row.message === live.asked
  if (answered || waiting) return null
  return elsewhere && at === rows.length - 1 && !live ? 'elsewhere' : 'unanswered'
}

type ReaderTurnProps = { message: ChatMessage; status: 'unanswered' | 'elsewhere' | null }

function ReaderTurn({ message, status }: ReaderTurnProps) {
  return (
    <>
      <div className="t-turn user">
        <div className="t-bub">
          {message.quote && <span className="t-q">{message.quote}</span>}
          {message.text}
        </div>
      </div>
      {status === 'unanswered' && (
        <div className="t-turn gloss">
          <p className="t-note quiet">답을 받지 못했어요.</p>
        </div>
      )}
      {status === 'elsewhere' && (
        <div className="t-turn gloss">
          <p className="t-note quiet pulse">답하는 중</p>
        </div>
      )}
    </>
  )
}

function GlossTurn({ message }: { message: ChatMessage }) {
  const note = noteOf(message)
  return (
    <div className="t-turn gloss">
      {message.text && <p>{message.text}</p>}
      {note && <Note note={note} />}
      {message.text && (
        <div className="t-act">
          <CopyButton text={message.text} />
        </div>
      )}
    </div>
  )
}

function Note({ note }: { note: ReplyNote | null }) {
  if (!note) return null
  return (
    <p className={note.quiet ? 't-note quiet' : 't-note'}>
      {note.text}
      {note.fix && <br />}
      {note.fix === 'key' && (
        <Link className="dl" href={KEY_HREF}>
          마이페이지에서 키 등록하기
        </Link>
      )}
      {note.fix === 'usage' && (
        <Link className="dl" href={USAGE_HREF}>
          마이페이지에서 한도 바꾸기
        </Link>
      )}
    </p>
  )
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), COPIED_FOR)
    return () => clearTimeout(timer)
  }, [copied])
  const label = copied ? '복사함' : '복사'
  return (
    <button
      type="button"
      className="ibtn"
      aria-label={label}
      data-tip={label}
      onClick={() => {
        void navigator.clipboard?.writeText(text).then(
          () => setCopied(true),
          () => {},
        )
      }}
    >
      <Icon name={copied ? 'check' : 'copy'} size={16} />
    </button>
  )
}
