'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { Icon } from '@/components/ui/Icon'
import {
  type ChatMessage,
  noteOf,
  paragraphsOf,
  type ReplyNote,
  type ThreadRow,
  threadRowsOf,
} from '@/features/reader/api/chat'
import { type LiveTurn, type Refused, shownOf } from '@/features/reader/utils/tutor'
import type { TocItem } from '@/lib/books'
import { COPIED_FOR } from '@/utils/copied'
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
 * The conversation as notes: the phrase a turn quotes, the reader's question in bold, Gloss's answer
 * under it. A question without a quote stays in the note before it. A divider names the item
 * wherever a turn comes from another one, and jumps there.
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
        if (message.role === 'gloss') return <Answer key={row.key} message={message} />
        const status = waitStatus({ rows, at: i, live, elsewhere })
        const opens = message.quote !== null || rows[i - 1]?.kind !== 'message'
        return <Question key={row.key} message={message} status={status} opens={opens} />
      })}
      {live && (
        <div className="t-a" aria-busy="true">
          {live.text ? (
            <Paragraphs text={live.text} cursor />
          ) : (
            <p className="t-pending">생각 중</p>
          )}
        </div>
      )}
      {refused && (
        <div className="t-a" role="alert">
          <Note note={noteOf({ status: 'failed', code: refused.code, message: refused.message })} />
        </div>
      )}
      {loadFailed && (
        <div className="t-a">
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
function waitStatus({ rows, at, live, elsewhere }: WaitArgs): QuestionProps['status'] {
  const row = rows[at]
  const next = rows[at + 1]
  const answered = next?.kind === 'message' && next.message.role === 'gloss'
  const waiting = live !== null && row?.kind === 'message' && row.message === live.asked
  if (answered || waiting) return null
  return elsewhere && at === rows.length - 1 && !live ? 'elsewhere' : 'unanswered'
}

type QuestionProps = {
  message: ChatMessage
  status: 'unanswered' | 'elsewhere' | null
  /** A quote, or the first turn after a divider, starts a note of its own. */
  opens: boolean
}

function Question({ message, status, opens }: QuestionProps) {
  return (
    <>
      <div className={opens ? 't-ask opens' : 't-ask'}>
        {message.quote && (
          <p className="t-mark">
            <mark>{message.quote}</mark>
          </p>
        )}
        <p className="t-qtext">{message.text}</p>
      </div>
      {status === 'unanswered' && (
        <div className="t-a">
          <p className="t-note quiet">답을 받지 못했어요.</p>
        </div>
      )}
      {status === 'elsewhere' && (
        <div className="t-a">
          <p className="t-note quiet pulse">답하는 중</p>
        </div>
      )}
    </>
  )
}

function Answer({ message }: { message: ChatMessage }) {
  const note = noteOf(message)
  return (
    <div className="t-a">
      {message.text && <Paragraphs text={message.text} />}
      {note && <Note note={note} />}
      {message.text && (
        <div className="t-act">
          <CopyButton text={message.text} />
        </div>
      )}
    </div>
  )
}

function Paragraphs({ text, cursor = false }: { text: string; cursor?: boolean }) {
  const paragraphs = paragraphsOf(text)
  return paragraphs.map((paragraph, i) => (
    <p key={paragraph.at}>
      {paragraph.text}
      {cursor && i === paragraphs.length - 1 && <span className="cur" aria-hidden="true" />}
    </p>
  ))
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
