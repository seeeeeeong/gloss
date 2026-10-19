import type { TocItem } from '@/lib/books'
import { headingOf } from '@/lib/posts'

/** A message as a conversation lists it. A reply has a status; the reader's has none. */
export type ChatMessage = {
  /** The conversation it was said in. */
  sessionId: string
  seq: number
  role: 'reader' | 'gloss'
  /** The page's own name for a reader's turn; null on Gloss's replies. */
  turnId: string | null
  /** The reader's message a reply answers; null on the reader's own. */
  replyTo: number | null
  itemKey: string | null
  quote: string | null
  text: string
  status: 'done' | 'stopped' | 'cut' | 'failed' | null
  code: string | null
  message: string | null
  createdAt: string
}

/**
 * One of the book's conversations; no [sessionId] before the first question. [answering] is true
 * while a turn is under way, from this page or another, until its reply is kept.
 */
export type Chat = { sessionId: string | null; messages: ChatMessage[]; answering: boolean }

/** A conversation as the history lists it: its first question and the item it started on. */
export type ChatSession = {
  id: string
  firstQuestion: string
  itemKey: string | null
  /** Every item a turn of it came from. */
  itemKeys: string[]
  startedAt: string
  lastActivityAt: string
}

/** A phrase picked in the book, with the item it was picked in. */
export type Quote = { text: string; itemKey: string | null }

/** The conversation with the newest activity. */
export const chatPath = (bookId: string) => `/api/me/books/${bookId}/chat`
export const sessionsPath = (bookId: string) => `${chatPath(bookId)}/sessions`
export const sessionPath = (bookId: string, sessionId: string) =>
  `${sessionsPath(bookId)}/${sessionId}`
export const turnsPath = (bookId: string) => `${chatPath(bookId)}/turns`
export const stopPath = (bookId: string) => `${chatPath(bookId)}/stop`

// As the server takes it.
export const MAX_QUESTION = 2000
// A sentence or two, what a reader means by "this": a longer pick is the article itself, which the
// model already has, so it is cut rather than sent again. The server allows 1,000 for other callers.
const MAX_QUOTE = 280

/** A selection as the quote row shows it: one line, cut with an ellipsis past 280 characters. */
export function quoteOf(selected: string): string {
  const line = selected.replace(/\s+/g, ' ').trim()
  return line.length > MAX_QUOTE ? `${line.slice(0, MAX_QUOTE)}…` : line
}

/** One server-sent event of a turn, its data parsed. */
export type TurnEvent =
  | { name: 'asked'; message: ChatMessage }
  | { name: 'text'; text: string }
  | { name: 'answered'; message: ChatMessage }

/**
 * The whole events in [buffer], and what is left of it for the next chunk. A comment, sent to keep
 * the connection open, and an event of a name this page does not know are skipped.
 */
export function readEvents(buffer: string): { events: TurnEvent[]; rest: string } {
  const frames = buffer.replace(/\r\n/g, '\n').split('\n\n')
  const rest = frames.pop() ?? ''
  const events: TurnEvent[] = []
  for (const frame of frames) {
    let name = ''
    let data = ''
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5).trimStart()
    }
    const event = eventOf(name, data)
    if (event) events.push(event)
  }
  return { events, rest }
}

function eventOf(name: string, data: string): TurnEvent | null {
  if (!data) return null
  if (name === 'text') return { name, text: (JSON.parse(data) as { text: string }).text }
  if (name === 'asked' || name === 'answered')
    return { name, message: JSON.parse(data) as ChatMessage }
  return null
}

/** A row of the thread: a message, or the divider naming the item a turn was asked from. */
export type ThreadRow =
  | { kind: 'divider'; key: string; itemKey: string; label: string; known: boolean }
  | { kind: 'message'; key: string; message: ChatMessage }

/**
 * The thread with a divider wherever a reader's turn comes from another item than the turn before
 * it, the first included. An item no longer in the contents is named by its key and does not jump.
 */
export function threadRowsOf(messages: ChatMessage[], items: TocItem[]): ThreadRow[] {
  const byKey = new Map(items.map((item) => [item.itemKey, item]))
  const rows: ThreadRow[] = []
  let previous: string | null = null
  for (const message of messages) {
    const at = message.itemKey
    if (message.role === 'reader' && at !== null && at !== previous) {
      const item = byKey.get(at)
      const label = item ? headingOf(item) : (at.split(' > ').pop() ?? at)
      rows.push({ kind: 'divider', key: `d${message.seq}`, itemKey: at, label, known: !!item })
    }
    if (message.role === 'reader' && at !== null) previous = at
    rows.push({ kind: 'message', key: `m${message.seq}`, message })
  }
  return rows
}

/** An answer's paragraphs, split at blank lines, each with where it starts in the text. */
export function paragraphsOf(text: string): { at: number; text: string }[] {
  const paragraphs: { at: number; text: string }[] = []
  let at = 0
  for (const blank of text.matchAll(/\n(?:[ \t]*\n)+/g)) {
    paragraphs.push({ at, text: text.slice(at, blank.index) })
    at = blank.index + blank[0].length
  }
  paragraphs.push({ at, text: text.slice(at) })
  const written = paragraphs.filter((paragraph) => paragraph.text.trim() !== '')
  return written.length > 0 ? written : [{ at: 0, text }]
}

/** What a reply that did not finish tells the reader, and where they can fix it. */
export type ReplyNote = { text: string; fix: 'key' | 'usage' | null; quiet: boolean }

const KEY_CODES = ['llm.key_rejected', 'llm.key_unreadable']

/** For a reply that is not done, or a turn the server refused; null for an answer that finished. */
export function noteOf(reply: Pick<ChatMessage, 'status' | 'code' | 'message'>): ReplyNote | null {
  const { status, code, message } = reply
  if (status === 'done') return null
  if (status === 'stopped') return { text: '답을 멈췄어요.', fix: null, quiet: true }
  if (status === 'cut') return { text: '답이 중간에 끊겼어요.', fix: null, quiet: true }
  if (code === 'llm.no_key')
    return { text: '튜터에게 물으려면 DeepSeek API 키가 필요해요.', fix: 'key', quiet: false }
  if (code === 'budget.limit_reached')
    return { text: '이번 달 한도에 닿아서 답하지 못했어요.', fix: 'usage', quiet: false }
  const fix = KEY_CODES.includes(code ?? '')
    ? 'key'
    : code === 'budget.limit_not_set'
      ? 'usage'
      : null
  return { text: message ?? '답을 받지 못했어요.', fix, quiet: false }
}

/**
 * The item a question is asked from: the picked phrase's, else the one being read, else on the cover
 * or the 차례 the latest turn's, while the contents still hold it.
 */
export function itemKeyFor(
  quote: Quote | null,
  current: string | null,
  messages: ChatMessage[] | null,
  items: TocItem[],
): string | null {
  if (quote?.itemKey) return quote.itemKey
  if (current) return current
  const keys = new Set(items.map((item) => item.itemKey))
  const last = messages?.findLast((message) => message.itemKey !== null)?.itemKey ?? null
  return last !== null && keys.has(last) ? last : null
}
