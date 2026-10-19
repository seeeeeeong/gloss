import type { ChatMessage, ChatSession, Quote } from '@/features/reader/api/chat'

/** The turn being answered: the reader's message, as kept once the server has it, and the answer so far. */
export type LiveTurn = { asked: ChatMessage; text: string }

/** A turn the server refused before keeping it: the reader's words go back into the composer. */
export type Refused = { code: string; message: string; text: string; quote: Quote | null }

/**
 * One book's conversations as the page holds them: the one open, its messages null until the first
 * read comes back, and the list of them, null until read.
 */
export type TutorState = {
  bookId: string
  /** The conversation open; null for a new one, before its first question is kept. */
  sessionId: string | null
  /** Counts each conversation opened, so a read for one left behind changes nothing. */
  view: number
  sessions: ChatSession[] | null
  messages: ChatMessage[] | null
  loadFailed: boolean
  live: LiveTurn | null
  refused: Refused | null
  /** Another tab of this book is being answered; the page reads along until its reply is kept. */
  elsewhere: boolean
}

/** Each change names the book it was asked for, so one that comes back after the reader moved on is dropped. */
export type TutorAction =
  | { type: 'reset'; bookId: string }
  | {
      type: 'loaded'
      bookId: string
      messages: ChatMessage[]
      answering?: boolean
      /** The conversation read, and the view it was read for. */
      sessionId?: string | null
      view?: number
    }
  | { type: 'loadFailed'; bookId: string; view?: number }
  | { type: 'opened'; bookId: string; sessionId: string | null; view: number }
  | { type: 'listed'; bookId: string; sessions: ChatSession[] }
  | { type: 'removed'; bookId: string; sessionId: string }
  | { type: 'asking'; bookId: string; asked: ChatMessage }
  | { type: 'asked'; bookId: string; asked: ChatMessage }
  | { type: 'text'; bookId: string; piece: string }
  | { type: 'answered'; bookId: string; asked: ChatMessage; reply: ChatMessage }
  | { type: 'refused'; bookId: string; refused: Refused }
  | { type: 'ended'; bookId: string }
  | { type: 'dismissed'; bookId: string }
  | { type: 'elsewhere'; bookId: string; on: boolean }

export const tutorOf = (bookId: string): TutorState => ({
  bookId,
  sessionId: null,
  view: 0,
  sessions: null,
  messages: null,
  loadFailed: false,
  live: null,
  refused: null,
  elsewhere: false,
})

/** [incoming] laid over [base] by sequence number, in order; a message not yet kept has none. */
export function mergeBySeq(base: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const bySeq = new Map(base.filter((m) => m.seq > 0).map((m) => [m.seq, m]))
  for (const message of incoming) if (message.seq > 0) bySeq.set(message.seq, message)
  return [...bySeq.values()].sort((a, b) => a.seq - b.seq)
}

// A turn under way that this page did not send is another tab's, still being answered.
const elsewhereAfter = (state: TutorState, answering: boolean | undefined) =>
  answering === undefined ? state.elsewhere : answering && state.live === null

type Action<T extends TutorAction['type']> = Extract<TutorAction, { type: T }>

function loadedInto(state: TutorState, action: Action<'loaded'>): TutorState {
  if (!readsOpen(state, action)) return state
  return {
    ...state,
    sessionId: action.sessionId ?? state.sessionId,
    loadFailed: false,
    messages: mergeBySeq(state.messages ?? [], action.messages),
    elsewhere: elsewhereAfter(state, action.answering),
  }
}

function openedIn(state: TutorState, action: Action<'opened'>): TutorState {
  return {
    ...state,
    sessionId: action.sessionId,
    view: action.view,
    // A new conversation has nothing to read; one from the history is read next.
    messages: action.sessionId === null ? [] : null,
    loadFailed: false,
    live: null,
    refused: null,
    elsewhere: false,
  }
}

// The turn's message as kept: a new conversation takes the id it was given, and moves up the list.
function askedIn(state: TutorState, asked: ChatMessage): TutorState {
  if (!state.live) return state
  return {
    ...state,
    sessionId: state.sessionId ?? asked.sessionId,
    sessions: touchSession(state.sessions, asked),
    live: { ...state.live, asked },
  }
}

/**
 * Whether a read is of the conversation open: one for a view since left, or of another conversation
 * than the one open, is dropped; a new conversation takes the one its first question went into.
 */
function readsOpen(state: TutorState, action: { sessionId?: string | null; view?: number }) {
  if (action.view !== undefined && action.view !== state.view) return false
  const read = action.sessionId
  return read === undefined || read === null || state.sessionId === null || read === state.sessionId
}

/**
 * A read and a turn may finish in either order: the read may already hold the turn the reader just
 * sent, or come back after it was answered, and the thread shows each message once either way.
 */
export function tutorReducer(state: TutorState, action: TutorAction): TutorState {
  if (action.type === 'reset')
    return action.bookId === state.bookId ? state : tutorOf(action.bookId)
  if (action.bookId !== state.bookId) return state
  switch (action.type) {
    case 'loaded':
      return loadedInto(state, action)
    case 'loadFailed':
      return action.view !== undefined && action.view !== state.view
        ? state
        : { ...state, loadFailed: true }
    case 'opened':
      return openedIn(state, action)
    case 'listed':
      return { ...state, sessions: action.sessions }
    case 'removed':
      return { ...state, sessions: (state.sessions ?? []).filter((s) => s.id !== action.sessionId) }
    case 'asking':
      return { ...state, refused: null, live: { asked: action.asked, text: '' } }
    case 'asked':
      return askedIn(state, action.asked)
    case 'text':
      return state.live
        ? { ...state, live: { ...state.live, text: state.live.text + action.piece } }
        : state
    case 'answered':
      return {
        ...state,
        live: null,
        messages: mergeBySeq(state.messages ?? [], [action.asked, action.reply]),
      }
    case 'refused':
      return { ...state, live: null, refused: action.refused }
    case 'ended':
      return { ...state, live: null }
    case 'dismissed':
      return { ...state, refused: null }
    case 'elsewhere':
      return { ...state, elsewhere: action.on }
  }
}

/**
 * The thread to show: what is kept, then the turn being answered. A read that came back during the
 * turn may hold its message already, kept or not yet numbered here; it shows once.
 */
export function shownOf(messages: ChatMessage[], live: LiveTurn | null): ChatMessage[] {
  if (!live) return messages
  const { asked } = live
  const kept = messages.filter((m) => m.seq !== asked.seq && m.turnId !== asked.turnId)
  return [...kept, asked]
}

/** The siblings an open modal sets aside, and how to bring them back. */
export type Inert = { inert: boolean }

/**
 * Makes every one of [siblings] that [keep] does not hold inert, so Tab and a screen reader stay in
 * the modal; the returned function undoes only what it did, then hands focus back.
 */
export function holdOthers<T extends Inert>(
  siblings: Iterable<T>,
  keep: (el: T) => boolean,
  returnFocus: () => void,
): () => void {
  const held = [...siblings].filter((el) => !keep(el) && !el.inert)
  for (const el of held) el.inert = true
  return () => {
    for (const el of held) el.inert = false
    returnFocus()
  }
}

/** Whether a reply to the reader's message [seq] has been kept, whatever way it ended. */
export const repliedTo = (messages: ChatMessage[], seq: number) =>
  messages.some((message) => message.role === 'gloss' && message.replyTo === seq)

/** Reads until [done] says so or the tries run out, waiting between reads; the last read is returned. */
export async function pollUntil<T>(
  read: () => Promise<T>,
  done: (value: T, reads: number) => boolean,
  { tries, wait }: { tries: number; wait: () => Promise<void> },
): Promise<T> {
  let value = await read()
  for (let reads = 1; reads < tries && !done(value, reads); reads++) {
    await wait()
    value = await read()
  }
  return value
}

/**
 * How a turn the page sent, with no word back from the server, stands in [messages]: found by the id
 * the page gave it, so a question asked in the same words before is never taken for it.
 */
export function turnIn(messages: ChatMessage[], turnId: string): 'absent' | 'kept' | 'answered' {
  const asked = messages.find((m) => m.role === 'reader' && m.turnId === turnId)
  if (!asked) return 'absent'
  return repliedTo(messages, asked.seq) ? 'answered' : 'kept'
}

/** Whether the tutor docks as a book opens, from what the browser kept: only once the reader docked it. */
export const startsDocked = (kept: string | null) => kept === 'true'

// The book needs this much beside a docked tutor of 380: one page of text a reader can follow.
export const MIN_READER = 480
export const TUTOR_WIDTH = 380

/**
 * Whether the tutor docks beside the book, from the width the book has now: with the tutor docked
 * that width is what is left beside it, without it the tutor's 380 would come out of it. The same
 * answer either way at a given window, so it does not flip as the panel opens or closes.
 */
export function tutorDocks(stageWidth: number, docked: boolean): boolean {
  return (docked ? stageWidth : stageWidth - TUTOR_WIDTH) >= MIN_READER
}

/**
 * Whether the window is too narrow to dock the tutor, judged when a new [width] is measured; null
 * when it is the width already judged. Opening or closing the panel changes [docked] a moment before
 * the width it makes is measured, and the width from before would be judged against the wrong state.
 */
export function narrowAt(judgedWidth: number, width: number, docked: boolean): boolean | null {
  if (width <= 0 || width === judgedWidth) return null
  return !tutorDocks(width, docked)
}

// Room the pill needs over a phrase before it goes under it instead, below the header.
const ABOVE_MIN = 60
const PILL_HEIGHT = 36
const PILL_GAP = 8

/**
 * Where "Gloss에게 묻기" goes over a selected phrase: above it with a mouse, below it on touch, where
 * the system's own menu takes the space above; whichever side lacks room gives way to the other,
 * and it stays on screen either way.
 */
export function pillTop(
  rect: { top: number; bottom: number },
  viewportHeight: number,
  coarse: boolean,
): number {
  const above = rect.top - PILL_HEIGHT - PILL_GAP
  const below = rect.bottom + PILL_GAP
  const roomAbove = above >= ABOVE_MIN
  const roomBelow = below + PILL_HEIGHT <= viewportHeight - PILL_GAP
  const top = (coarse ? roomBelow || !roomAbove : !roomAbove && roomBelow) ? below : above
  return Math.max(ABOVE_MIN, Math.min(viewportHeight - PILL_HEIGHT - PILL_GAP, top))
}

/** Whether the panel has a thread to show: once read, or as soon as a turn is sent or refused, read or not. */
export const showsThread = (
  state: Pick<TutorState, 'messages' | 'loadFailed' | 'live' | 'refused'>,
) => state.messages !== null || state.loadFailed || state.live !== null || state.refused !== null

/** Read, and nothing yet said or on its way: the panel shows only the mark. */
export const isQuiet = (state: Pick<TutorState, 'messages' | 'loadFailed' | 'live' | 'refused'>) =>
  state.messages !== null &&
  state.messages.length === 0 &&
  !state.loadFailed &&
  state.live === null &&
  state.refused === null

// An answer may wait two minutes for its first piece and run twenty (the server's idle timeout and
// call deadline); another tab's turn is followed that long and a little more.
export const FOLLOW_LIMIT_MS = 22 * 60_000
const FOLLOW_FIRST_MS = 3000
const FOLLOW_MOST_MS = 10_000

/** The wait before the next read, growing from three seconds to ten as an answer runs on. */
export const followWait = (reads: number) =>
  Math.min(FOLLOW_MOST_MS, Math.round(FOLLOW_FIRST_MS * 1.5 ** (reads - 1)))

/**
 * Reads while [read] says the turn is still under way, waiting longer between reads as it runs, up
 * to [limitMs] of waiting; returns whether the server said it ended.
 */
export async function followWhileAnswering(
  read: () => Promise<boolean>,
  sleep: (ms: number) => Promise<void>,
  limitMs = FOLLOW_LIMIT_MS,
): Promise<boolean> {
  let waited = 0
  for (let reads = 1; await read(); reads++) {
    const wait = followWait(reads)
    if (waited + wait > limitMs) return false
    await sleep(wait)
    waited += wait
  }
  return true
}

/** The items the reader has talked with the tutor about, in any of the book's conversations. */
export function talkedItems(
  sessions: ChatSession[] | null,
  messages: ChatMessage[] | null,
): ReadonlySet<string> {
  return new Set([
    ...(sessions ?? []).flatMap((session) => session.itemKeys),
    ...(messages ?? []).flatMap((message) => message.itemKey ?? []),
  ])
}

/** The list once [asked] is kept: its conversation moved to the top, or added there by its first question. */
export function touchSession(
  sessions: ChatSession[] | null,
  asked: ChatMessage,
): ChatSession[] | null {
  if (sessions === null) return null
  const at = asked.createdAt
  const found = sessions.find((session) => session.id === asked.sessionId)
  const itemKeys = (keys: string[]) =>
    asked.itemKey === null || keys.includes(asked.itemKey) ? keys : [...keys, asked.itemKey]
  const touched: ChatSession = found
    ? { ...found, itemKeys: itemKeys(found.itemKeys), lastActivityAt: at }
    : {
        id: asked.sessionId,
        firstQuestion: asked.text,
        itemKey: asked.itemKey,
        itemKeys: itemKeys([]),
        startedAt: at,
        lastActivityAt: at,
      }
  return [touched, ...sessions.filter((session) => session.id !== asked.sessionId)]
}

/** A row of the history: a conversation, and whether it is the one open. */
export type HistoryRow = { session: ChatSession; open: boolean }

/** The book's conversations, newest activity first, the one open marked. */
export function historyRowsOf(sessions: ChatSession[], openId: string | null): HistoryRow[] {
  return [...sessions]
    .sort(
      (a, b) =>
        Date.parse(b.lastActivityAt) - Date.parse(a.lastActivityAt) || (a.id < b.id ? 1 : -1),
    )
    .map((session) => ({ session, open: session.id === openId }))
}

const DAY_MS = 24 * 60 * 60 * 1000

/** When a conversation was last had, by the reader's calendar: 오늘, 어제, 10월 12일, or with its year. */
export function relativeDay(iso: string, now: Date): string {
  const at = new Date(iso)
  const day = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const days = Math.round((day(now) - day(at)) / DAY_MS)
  if (days === 0) return '오늘'
  if (days === 1) return '어제'
  const date = `${at.getMonth() + 1}월 ${at.getDate()}일`
  return at.getFullYear() === now.getFullYear() ? date : `${at.getFullYear()}년 ${date}`
}

/** What the empty composer asks for: about the phrase quoted, more after an answer, else the post. */
export function promptOf(quote: Quote | null, messages: ChatMessage[] | null): string {
  if (quote) return '이 부분에 대해 물어보세요'
  const answered = (messages ?? []).some((message) => message.role === 'gloss' && message.text)
  return answered ? '이어서 물어보세요' : '이 글에 대해 물어보세요'
}

export type SheetSize = 'half' | 'full' | 'closed'

// A drag shorter than this leaves the sheet where it was.
const SHEET_DRAG = 48

/** Where a drag of [dy] leaves the sheet: up to full height, down a step, or closed from half. */
export function sheetAfterDrag(size: Exclude<SheetSize, 'closed'>, dy: number): SheetSize {
  if (dy < -SHEET_DRAG) return 'full'
  if (dy > SHEET_DRAG) return size === 'full' ? 'half' : 'closed'
  return size
}
