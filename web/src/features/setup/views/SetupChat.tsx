'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useRef } from 'react'
import { Icon } from '@/components/ui/Icon'
import {
  NEW_CHAT,
  type OfferedBook,
  readerLine,
  readOnly,
  type SetupSession,
  showsTurn,
} from '@/features/setup/api/setup'
import { Composer } from '@/features/setup/components/Composer'
import type { ShownContents } from '@/features/setup/components/SetupContents'
import { GlossTurn } from '@/features/setup/components/SetupTurns'
import { useHeldContents } from '@/features/setup/hooks/useHeldContents'
import { type Pending, useSetupSession } from '@/features/setup/hooks/useSetupSession'

// As the server takes them: a question in a few sentences.
const MAX_TEXT = 500

/**
 * Adding a book by talking it through: the reader names a book or asks what to read, Gloss answers
 * with the editions found or the books it recommends, a pick brings its contents into the thread as a
 * card that opens them in the side panel, and starting with them makes the book. Empty, the screen is
 * a heading over the composer; once the book is made, the thread is only read.
 */
export function SetupChat({ initial }: { initial: SetupSession | null }) {
  const path = usePathname()
  const setup = useSetupSession(initial)
  const { session, pending, error, reset } = setup
  const held = useHeldContents(setup)
  const resetHeld = held.reset

  // "새 책" from the sidebar comes back here without a session: a new thread.
  const fresh = path === NEW_CHAT
  const wasFresh = useRef(fresh)
  useEffect(() => {
    const cameBack = fresh && !wasFresh.current
    wasFresh.current = fresh
    if (!cameBack) return
    reset()
    resetHeld()
  }, [fresh, reset, resetHeld])

  const composer = (docked: boolean) => (
    <Composer
      label="메시지"
      placeholder={docked ? undefined : 'Gloss에게 물어보세요'}
      maxLength={MAX_TEXT}
      busy={pending !== null || session?.searching === true}
      autoFocus={docked}
      onSend={setup.say}
    />
  )

  if (!session && !pending && !error) {
    return (
      <div className="home">
        <div className="wrap">
          <div className="col">
            <h1>어떤 도서를 학습할까요?</h1>
            {composer(false)}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="scroll">
      <div className="wrap">
        <div className="col thread">
          <Link className="tbtn new-thread" href="/books/new">
            <Icon name="plus" size={16} />새 대화
          </Link>
          <Turns
            session={session}
            pending={pending}
            contents={held.contents}
            onPick={(book) => void setup.pick(book)}
          />
          {error && (
            <div className="turn gloss">
              <p role="alert">{error}</p>
            </div>
          )}
        </div>
      </div>
      {!readOnly(session) && (
        <div className="dock">
          <div className="wrap">
            <div className="col">{composer(true)}</div>
          </div>
        </div>
      )}
      {held.panel}
    </div>
  )
}

type TurnsProps = {
  session: SetupSession | null
  pending: Pending | null
  contents: ShownContents | null
  onPick: (book: OfferedBook) => void
}

/** The thread so far, then what the reader just did while the server answers it. */
function Turns({ session, pending, contents, onPick }: TurnsProps) {
  const lastTurn = useRef<HTMLDivElement>(null)
  const messages = session?.messages ?? []
  const lastReader = pending ? -1 : messages.findLastIndex((message) => message.role === 'reader')

  // The reader's latest turn at the top, so Gloss's answer reads down from it.
  useEffect(() => {
    if (messages.length > 0 || pending) lastTurn.current?.scrollIntoView({ block: 'start' })
  }, [messages.length, pending])

  return (
    <>
      {session &&
        messages.map((message, i) => {
          // The model's own steps show inside the answer they led to.
          if (!showsTurn(message)) return null
          if (message.role === 'reader')
            return (
              <div
                className="turn user"
                key={message.seq}
                ref={i === lastReader ? lastTurn : undefined}
              >
                <div className="bubble">{readerLine(message)}</div>
              </div>
            )
          return (
            <div className="turn gloss" key={message.seq}>
              <p className="speaker" aria-hidden="true">
                gloss
              </p>
              <GlossTurn
                message={message}
                session={session}
                busy={pending !== null}
                contents={contents}
                onPick={onPick}
              />
            </div>
          )
        })}
      {!pending && session?.searching && (
        <div className="turn gloss">
          <p className="think pulse" role="status">
            찾는 중
          </p>
        </div>
      )}
      {pending && (
        <>
          <div className="turn user" ref={lastTurn}>
            <div className="bubble">{pending.text}</div>
          </div>
          <div className="turn gloss">
            <p className="think pulse">{pending.kind === 'say' ? '찾는 중' : '목차 찾는 중'}</p>
          </div>
        </>
      )}
    </>
  )
}
