'use client'

import { useCallback, useEffect, useReducer, useRef } from 'react'
import {
  type Chat,
  type ChatMessage,
  type ChatSession,
  chatPath,
  type Quote,
  readEvents,
  sessionPath,
  sessionsPath,
  stopPath,
  type TurnEvent,
  turnsPath,
} from '@/features/reader/api/chat'
import {
  followWhileAnswering,
  pollUntil,
  repliedTo,
  type TutorAction,
  turnIn,
  tutorOf,
  tutorReducer,
} from '@/features/reader/utils/tutor'
import { changeHeaders, load, readResult, send, UNREACHED } from '@/lib/api'
import { announceRecent } from '@/lib/reads'

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
 * A reader's conversations with the tutor about one book: the most recent opened when the page
 * opens, another from the history or a new one on the reader's word, then a turn at a time, streamed
 * as Gloss writes. Whatever way a turn ends, the thread is the server's. Every answer that comes
 * back names its book and the conversation it was read for, so one the reader has left changes nothing.
 */
export function useTutor(bookId: string) {
  const [state, dispatch] = useReducer(tutorReducer, bookId, tutorOf)
  const shown = state.bookId === bookId ? state : tutorOf(bookId)
  const controller = useRef<AbortController | null>(null)
  const turn = useRef<string | null>(null)
  // The conversation open, as the next read or turn should name it; moved at once on a switch.
  const at = useRef<Open>({ sessionId: null, view: 0 })
  useEffect(() => {
    if (shown.view === at.current.view) at.current = { ...at.current, sessionId: shown.sessionId }
  }, [shown.view, shown.sessionId])

  // One read-along at a time, and none once the page has left the book or the conversation.
  const following = useRef<string | null>(null)
  const follow = useCallback(() => {
    const open = at.current
    const token = `${bookId}:${open.view}`
    if (following.current === token) return
    following.current = token
    void followElsewhere({ bookId, open, dispatch }, () => following.current === token).finally(
      () => {
        if (following.current === token) following.current = null
      },
    )
  }, [bookId])

  const list = useCallback(async () => {
    const result = await load<ChatSession[]>(sessionsPath(bookId))
    if (result.ok) dispatch({ type: 'listed', bookId, sessions: result.data })
  }, [bookId])

  const reload = useCallback(async () => {
    const open = at.current
    const result = await load<Chat>(readPathOf(bookId, open.sessionId))
    if (!result.ok) {
      dispatch({ type: 'loadFailed', bookId, view: open.view })
      return
    }
    const { sessionId, messages, answering } = result.data
    dispatch({ type: 'loaded', bookId, messages, answering, sessionId, view: open.view })
    if (answering && controller.current === null) follow()
  }, [bookId, follow])

  useEffect(() => {
    at.current = { sessionId: null, view: 0 }
    dispatch({ type: 'reset', bookId })
    void reload()
    void list()
    return () => {
      controller.current?.abort()
      controller.current = null
      following.current = null
    }
  }, [bookId, reload, list])

  /** Opens a conversation from the history, or a new one with [sessionId] null. */
  const open = useCallback(
    (sessionId: string | null) => {
      if (controller.current) return
      const view = at.current.view + 1
      at.current = { sessionId, view }
      following.current = null
      dispatch({ type: 'opened', bookId, sessionId, view })
      if (sessionId !== null) void reload()
    },
    [bookId, reload],
  )

  /** Deletes a conversation; the one open gives way to a new one. */
  const remove = useCallback(
    async (sessionId: string) => {
      const result = await send('DELETE', sessionPath(bookId, sessionId))
      const gone = result.ok || result.status === 404
      if (gone) {
        dispatch({ type: 'removed', bookId, sessionId })
        if (at.current.sessionId === sessionId) open(null)
        announceRecent()
      }
      void list()
      return result
    },
    [bookId, open, list],
  )

  /** Shows how a turn ended: refused with the reader's words back, answered, or read again until kept. */
  const finish = useCallback(
    async (outcome: Outcome, said: Said, open: Open) => {
      if (outcome.kind === 'refused') {
        for (const action of await afterRefusal({ bookId, open, dispatch }, outcome, said))
          dispatch(action)
        // Another tab is answering this conversation: read along, so its turn shows here too.
        if (outcome.code === ANSWERING) follow()
        return
      }
      if (outcome.kind === 'answered') {
        dispatch({ type: 'answered', bookId, asked: outcome.asked, reply: outcome.reply })
        return
      }
      for (const action of await afterBreak({ bookId, open, dispatch }, outcome.asked))
        dispatch(action)
    },
    [bookId, follow],
  )

  const ask = useCallback(
    async (question: Question) => {
      const { text, quote, itemKey, postVersionId } = question
      if (controller.current) return
      const abort = new AbortController()
      controller.current = abort
      const open = at.current
      // The page names the turn, so a stop pressed before the server answers still finds it.
      const turnId = crypto.randomUUID()
      turn.current = turnId
      dispatch({ type: 'asking', bookId, asked: pendingOf({ text, quote, turnId }, itemKey) })
      const outcome = await stream(
        {
          bookId,
          body: {
            text,
            itemKey,
            quote: quote?.text ?? null,
            turnId,
            postVersionId,
            sessionId: open.sessionId,
          },
        },
        {
          signal: abort.signal,
          onAsked: (asked) => dispatch({ type: 'asked', bookId, asked }),
          onText: (piece) => dispatch({ type: 'text', bookId, piece }),
        },
      )
      if (controller.current === abort) controller.current = null
      await finish(outcome, { text, quote, turnId }, open)
      void list()
      announceRecent()
    },
    [bookId, finish, list],
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

  const { sessionId, sessions, messages, loadFailed, live, refused, elsewhere } = shown
  return {
    sessionId,
    sessions,
    messages,
    loadFailed,
    live,
    refused,
    elsewhere,
    ask,
    stop,
    reload,
    dismissRefusal,
    open,
    remove,
    list,
  }
}

/** The conversation a read or a turn is for, and the view it was begun in. */
type Open = { sessionId: string | null; view: number }

/** Where the conversation open is read: by its id, or the most recent before it has one. */
const readPathOf = (bookId: string, sessionId: string | null) =>
  sessionId === null ? chatPath(bookId) : sessionPath(bookId, sessionId)

type Reading = { bookId: string; open: Open; dispatch: (action: TutorAction) => void }

/**
 * What a turn whose stream broke off shows: the server keeps the reply once it notices, so the
 * conversation is read until it is there. A new one's question never heard of finds no conversation.
 */
async function afterBreak(
  { bookId, open }: Reading,
  asked: ChatMessage | null,
): Promise<TutorAction[]> {
  const ended: TutorAction = { type: 'ended', bookId }
  const sessionId = open.sessionId ?? asked?.sessionId ?? null
  if (sessionId === null) return [ended]
  const result = await settled(sessionPath(bookId, sessionId), asked)
  if (!result.ok) return [ended]
  const { messages } = result.data
  return [{ type: 'loaded', bookId, messages, sessionId, view: open.view }, ended]
}

/** The conversation once the reply to [asked] is kept, or as it stands when the reads run out. */
function settled(path: string, asked: ChatMessage | null) {
  return pollUntil(
    () => load<Chat>(path),
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
async function followElsewhere({ bookId, open, dispatch }: Reading, alive: () => boolean) {
  dispatch({ type: 'elsewhere', bookId, on: true })
  await followWhileAnswering(async () => {
    if (!alive()) return false
    const read = await load<Chat>(readPathOf(bookId, open.sessionId))
    if (!read.ok) return true
    const { sessionId, messages, answering } = read.data
    dispatch({ type: 'loaded', bookId, messages, answering, sessionId, view: open.view })
    return answering
  }, sleep)
  if (alive()) dispatch({ type: 'elsewhere', bookId, on: false })
}

/**
 * What a refused turn shows. With no word back at all the request may still have reached the server
 * and been kept: then the thread shows it, and the reader's words do not come back to be sent twice.
 */
async function afterRefusal(
  { bookId, open }: Reading,
  refusal: { code: string; message: string },
  said: Said,
): Promise<TutorAction[]> {
  const refused: TutorAction = {
    type: 'refused',
    bookId,
    refused: { code: refusal.code, message: refusal.message, text: said.text, quote: said.quote },
  }
  if (refusal.code !== UNREACHED.code) return [refused]
  const read = await sentUnheard(readPathOf(bookId, open.sessionId), said.turnId)
  if (!read.ok) return [refused]
  const { sessionId, messages } = read.data
  const loaded: TutorAction = { type: 'loaded', bookId, messages, sessionId, view: open.view }
  const kept = turnIn(messages, said.turnId) !== 'absent'
  if (kept) return [loaded, { type: 'ended', bookId }]
  // A new conversation's question that never came finds the latest of the others; it is not this one.
  return open.sessionId === null ? [refused] : [loaded, refused]
}

/** Reads until the turn sent ends, or, if it never arrived, a few reads show it was not kept. */
function sentUnheard(path: string, turnId: string) {
  return pollUntil(
    () => load<Chat>(path),
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
    sessionId: '',
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
    sessionId: string | null
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
