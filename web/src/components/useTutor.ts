'use client'

import { useCallback, useEffect, useReducer, useRef } from 'react'
import { changeHeaders, load, readResult, send, UNREACHED } from '@/lib/api'
import {
  type Chat,
  type ChatMessage,
  chatPath,
  type Quote,
  readEvents,
  stopPath,
  type TurnEvent,
  turnsPath,
} from '@/lib/chat'
import {
  followWhileAnswering,
  pollUntil,
  repliedTo,
  type TutorAction,
  turnIn,
  tutorOf,
  tutorReducer,
} from '@/lib/tutor'

// After a stop the server ends the answer within a second; past this the page lets go of it.
const STOP_GRACE_MS = 5000
// A reader gone is noticed within the stream's five-second keep-alive, then the reply is kept:
// eight reads a second and a half apart cover it with room.
const SETTLE_TRIES = 8
const SETTLE_MS = 1500
// A turn that reached the server is kept within moments; not there after three reads, it never came.
const ABSENT_READS = 3
const ANSWERING = 'chat.answering'

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

/**
 * A reader's conversation with the tutor about one book: read when the page opens, then a turn at
 * a time, streamed as Gloss writes. Whatever way a turn ends, the thread is the server's. Every
 * answer that comes back names its book, so one for a book the reader has left changes nothing.
 */
export function useTutor(bookId: string) {
  const [state, dispatch] = useReducer(tutorReducer, bookId, tutorOf)
  const shown = state.bookId === bookId ? state : tutorOf(bookId)
  const controller = useRef<AbortController | null>(null)
  const turn = useRef<string | null>(null)

  // One read-along at a time, and none once the page has left the book.
  const following = useRef<string | null>(null)
  const follow = useCallback(() => {
    if (following.current === bookId) return
    following.current = bookId
    void followElsewhere(bookId, dispatch, () => following.current === bookId).finally(() => {
      if (following.current === bookId) following.current = null
    })
  }, [bookId])

  const reload = useCallback(async () => {
    const result = await load<Chat>(chatPath(bookId))
    if (!result.ok) {
      dispatch({ type: 'loadFailed', bookId })
      return
    }
    const { messages, answering } = result.data
    dispatch({ type: 'loaded', bookId, messages, answering })
    if (answering && controller.current === null) follow()
  }, [bookId, follow])

  useEffect(() => {
    dispatch({ type: 'reset', bookId })
    void reload()
    return () => {
      controller.current?.abort()
      controller.current = null
      following.current = null
    }
  }, [bookId, reload])

  /** Shows how a turn ended: refused with the reader's words back, answered, or read again until kept. */
  const finish = useCallback(
    async (outcome: Outcome, said: Said) => {
      if (outcome.kind === 'refused') {
        for (const action of await afterRefusal(bookId, outcome, said)) dispatch(action)
        // Another tab of the book is being answered: read along, so its turn shows here too.
        if (outcome.code === ANSWERING) follow()
        return
      }
      if (outcome.kind === 'answered') {
        dispatch({ type: 'answered', bookId, asked: outcome.asked, reply: outcome.reply })
        return
      }
      // The stream broke off: the server keeps the reply once it notices, so it is read until there.
      const result = await settled(bookId, outcome.asked)
      if (result.ok) dispatch({ type: 'loaded', bookId, messages: result.data.messages })
      dispatch({ type: 'ended', bookId })
    },
    [bookId, follow],
  )

  const ask = useCallback(
    async (question: Question) => {
      const { text, quote, itemKey, postVersionId } = question
      if (controller.current) return
      const abort = new AbortController()
      controller.current = abort
      // The page names the turn, so a stop pressed before the server answers still finds it.
      const turnId = crypto.randomUUID()
      turn.current = turnId
      dispatch({ type: 'asking', bookId, asked: pendingOf({ text, quote, turnId }, itemKey) })
      const outcome = await stream(
        { bookId, body: { text, itemKey, quote: quote?.text ?? null, turnId, postVersionId } },
        {
          signal: abort.signal,
          onAsked: (asked) => dispatch({ type: 'asked', bookId, asked }),
          onText: (piece) => dispatch({ type: 'text', bookId, piece }),
        },
      )
      if (controller.current === abort) controller.current = null
      await finish(outcome, { text, quote, turnId })
    },
    [bookId, finish],
  )

  const stop = useCallback(async () => {
    const abort = controller.current
    if (!abort) return
    const timer = setTimeout(() => abort.abort(), STOP_GRACE_MS)
    abort.signal.addEventListener('abort', () => clearTimeout(timer))
    const result = await send('POST', stopPath(bookId), { turnId: turn.current })
    if (!result.ok) abort.abort()
  }, [bookId])

  const dismissRefusal = useCallback(() => dispatch({ type: 'dismissed', bookId }), [bookId])

  const { messages, loadFailed, live, refused, elsewhere } = shown
  return { messages, loadFailed, live, refused, elsewhere, ask, stop, reload, dismissRefusal }
}

/** The conversation once the reply to [asked] is kept, or as it stands when the reads run out. */
function settled(bookId: string, asked: ChatMessage | null) {
  return pollUntil(
    () => load<Chat>(chatPath(bookId)),
    (read) => asked === null || (read.ok && repliedTo(read.data.messages, asked.seq)),
    { tries: asked ? SETTLE_TRIES : 1, wait: () => sleep(SETTLE_MS) },
  )
}

type Said = { text: string; quote: Quote | null; turnId: string }

/** What the reader asks: their words, the phrase picked, the item, and the version of its post they were shown. */
export type Question = {
  text: string
  quote: Quote | null
  itemKey: string | null
  postVersionId: string | null
}

/** Reads the conversation while another tab's turn is under way, showing each read, until it ends. */
async function followElsewhere(
  bookId: string,
  dispatch: (action: TutorAction) => void,
  alive: () => boolean,
) {
  dispatch({ type: 'elsewhere', bookId, on: true })
  await followWhileAnswering(async () => {
    if (!alive()) return false
    const read = await load<Chat>(chatPath(bookId))
    if (!read.ok) return true
    const { messages, answering } = read.data
    dispatch({ type: 'loaded', bookId, messages, answering })
    return answering
  }, sleep)
  dispatch({ type: 'elsewhere', bookId, on: false })
}

/**
 * What a refused turn shows. With no word back at all the request may still have reached the server
 * and been kept: then the thread shows it, and the reader's words do not come back to be sent twice.
 */
async function afterRefusal(
  bookId: string,
  refusal: { code: string; message: string },
  said: Said,
): Promise<TutorAction[]> {
  const refused: TutorAction = {
    type: 'refused',
    bookId,
    refused: { code: refusal.code, message: refusal.message, text: said.text, quote: said.quote },
  }
  if (refusal.code !== UNREACHED.code) return [refused]
  const read = await sentUnheard(bookId, said.turnId)
  if (!read.ok) return [refused]
  const loaded: TutorAction = { type: 'loaded', bookId, messages: read.data.messages }
  const kept = turnIn(read.data.messages, said.turnId) !== 'absent'
  return kept ? [loaded, { type: 'ended', bookId }] : [loaded, refused]
}

/** Reads until the turn sent ends, or, if it never arrived, a few reads show it was not kept. */
function sentUnheard(bookId: string, turnId: string) {
  return pollUntil(
    () => load<Chat>(chatPath(bookId)),
    (read, reads) => {
      if (!read.ok) return reads >= ABSENT_READS
      const stands = turnIn(read.data.messages, turnId)
      return stands === 'answered' || (stands === 'absent' && reads >= ABSENT_READS)
    },
    { tries: SETTLE_TRIES, wait: () => sleep(SETTLE_MS) },
  )
}

/** The reader's message as it shows until the server has kept it. */
function pendingOf(said: Said, itemKey: string | null): ChatMessage {
  return {
    seq: -1,
    role: 'reader',
    turnId: said.turnId,
    replyTo: null,
    itemKey,
    quote: said.quote?.text ?? null,
    text: said.text,
    status: null,
    code: null,
    message: null,
    createdAt: '',
  }
}

type Outcome =
  | { kind: 'answered'; asked: ChatMessage; reply: ChatMessage }
  | { kind: 'refused'; code: string; message: string }
  | { kind: 'broken'; asked: ChatMessage | null }

type Turn = {
  bookId: string
  body: {
    text: string
    itemKey: string | null
    quote: string | null
    turnId: string
    postVersionId: string | null
  }
}
type Listeners = {
  signal: AbortSignal
  onAsked: (asked: ChatMessage) => void
  onText: (piece: string) => void
}

/** Sends a turn and reads its events until the reply comes, the server refuses it, or the stream breaks. */
async function stream(turn: Turn, listeners: Listeners): Promise<Outcome> {
  const heard: Heard = { asked: null }
  try {
    const response = await fetch(turnsPath(turn.bookId), {
      method: 'POST',
      headers: await changeHeaders(),
      body: JSON.stringify(turn.body),
      signal: listeners.signal,
    })
    if (!response.ok || !response.body) {
      const result = await readResult(response)
      return result.ok ? { kind: 'broken', asked: null } : { kind: 'refused', ...result }
    }
    return await readTurn(response.body, listeners, heard)
  } catch {
    // Nothing was kept when the request never got an answer at all.
    return heard.asked ? { kind: 'broken', asked: heard.asked } : { kind: 'refused', ...UNREACHED }
  }
}

type Heard = { asked: ChatMessage | null }

async function readTurn(
  body: ReadableStream<Uint8Array>,
  listeners: Listeners,
  heard: Heard,
): Promise<Outcome> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) return { kind: 'broken', asked: heard.asked }
    const read = readEvents(buffer + decoder.decode(value, { stream: true }))
    buffer = read.rest
    for (const event of read.events) {
      const ended = heed(event, listeners, heard)
      if (ended) return ended
    }
  }
}

/** Hands an event on; the reply ends the turn. */
function heed(event: TurnEvent, listeners: Listeners, heard: Heard): Outcome | null {
  if (event.name === 'asked') {
    heard.asked = event.message
    listeners.onAsked(event.message)
    return null
  }
  if (event.name === 'text') {
    listeners.onText(event.text)
    return null
  }
  return heard.asked
    ? { kind: 'answered', asked: heard.asked, reply: event.message }
    : { kind: 'broken', asked: null }
}
