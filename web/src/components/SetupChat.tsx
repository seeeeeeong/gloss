'use client'

import { useSearchParams } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { Composer } from '@/components/Composer'
import type { ShownContents } from '@/components/SetupContents'
import { GlossTurn } from '@/components/SetupTurns'
import { TypedContentsForm } from '@/components/TypedContentsForm'
import { useHeldContents } from '@/components/useHeldContents'
import { type Pending, useSetupSession } from '@/components/useSetupSession'
import {
  type OfferedBook,
  readerLine,
  SESSION_PARAM,
  type SetupSession,
  seekingContents,
  showsTurn,
} from '@/lib/setup'

// As the server takes them: a question in a few sentences.
const MAX_TEXT = 500

/**
 * Adding a book by talking it through: the reader names a book or asks what to read, Gloss answers
 * with the editions found or the books it recommends, a pick brings its contents as a document into
 * the thread, 수정 opens them as text in the side panel, and starting with them makes the book. Empty,
 * the screen is a heading over the composer.
 */
export function SetupChat({ initial }: { initial: SetupSession | null }) {
  const params = useSearchParams()
  const [typing, setTyping] = useState<OfferedBook | 'blank' | null>(null)
  const setup = useSetupSession(initial)
  const { session, pending, error, reset } = setup
  const held = useHeldContents(setup)
  const resetHeld = held.reset

  // "새 책" from the sidebar comes back here without a session: a new thread.
  const fresh = params.get(SESSION_PARAM) === null
  const wasFresh = useRef(fresh)
  useEffect(() => {
    const cameBack = fresh && !wasFresh.current
    wasFresh.current = fresh
    if (!cameBack) return
    reset()
    resetHeld()
    setTyping(null)
  }, [fresh, reset, resetHeld])

  const composer = (docked: boolean) => (
    <Composer
      label="메시지"
      placeholder={docked ? undefined : 'Gloss에게 물어보세요'}
      maxLength={MAX_TEXT}
      busy={pending !== null || session?.state === 'confirmed' || session?.searching === true}
      autoFocus={docked}
      onSend={setup.say}
      plus={{
        label: '직접 입력',
        expanded: typing === 'blank',
        onClick: () => setTyping(typing === 'blank' ? null : 'blank'),
      }}
    />
  )

  if (!session && !pending && !error && typing === null) {
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
          <Turns
            session={session}
            pending={pending}
            contents={held.contents}
            onPick={(book) => {
              setTyping(null)
              void setup.pick(book)
            }}
            onType={setTyping}
          />
          {error && (
            <div className="turn gloss">
              <p role="alert">{error}</p>
            </div>
          )}
          {typing !== null && (
            <section className="turn gloss" aria-labelledby="typed">
              <h2 className="sub-h" id="typed">
                {typing === 'blank' ? '직접 입력' : '목차 입력'}
              </h2>
              {/* A new key starts the form over for the edition picked. */}
              <TypedContentsForm
                key={typing === 'blank' ? 'blank' : typing.ref}
                found={typing === 'blank' ? null : typing}
                onSubmit={async (typed) => {
                  const result = await setup.type(typed)
                  if (result?.ok) setTyping(null)
                  return result
                }}
                onCancel={() => setTyping(null)}
              />
            </section>
          )}
        </div>
      </div>
      <div className="dock">
        <div className="wrap">
          <div className="col">{composer(true)}</div>
        </div>
      </div>
      {held.editor}
    </div>
  )
}

type TurnsProps = {
  session: SetupSession | null
  pending: Pending | null
  contents: ShownContents | null
  onPick: (book: OfferedBook) => void
  onType: (book: OfferedBook) => void
}

/** The thread so far, then what the reader just did while the server answers it. */
function Turns({ session, pending, contents, onPick, onType }: TurnsProps) {
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
              <GlossTurn
                message={message}
                session={session}
                busy={pending !== null}
                contents={contents}
                onPick={onPick}
                onType={onType}
              />
            </div>
          )
        })}
      {!pending && session?.searching && (
        <div className="turn gloss">
          <p className="think pulse" role="status">
            {seekingContents(session) ? '목차 찾는 중' : '찾는 중'}
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
