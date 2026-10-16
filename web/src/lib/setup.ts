import { type BookCandidate, isBookId, type TocNode, treeOf } from './books'

/** An edition a search in a setup session found, as the source describes it now: none of it is kept. */
export type OfferedBook = Pick<
  BookCandidate,
  | 'ref'
  | 'source'
  | 'url'
  | 'title'
  | 'authors'
  | 'publisher'
  | 'publishedOn'
  | 'kind'
  | 'hasContents'
> & { cover: string | null }

/** What a session keeps of an edition a search showed: enough to ask for it again, nothing more. */
export type FoundRef = { ref: string; hasContents: boolean }

type Message<R extends string, K extends string, P> = {
  seq: number
  role: R
  kind: K
  payload: P
  createdAt: string
}

/** A page the model cited for a book it recommended: kept only so the reader can open it. */
export type CitedPage = { url: string; title: string }

export type RecommendedBook = { ref: string; reason: string; sources: CitedPage[] }

/** One message of a setup session; its payload depends on its kind. */
export type SetupMessage =
  | Message<'reader', 'text', { text: string }>
  | Message<'reader', 'pick', { ref: string; title: string }>
  | Message<'reader', 'typed', { title: string }>
  | Message<'reader', 'confirm', { tocId: string }>
  | Message<'gloss', 'books', { query: string; books: FoundRef[]; callId?: string | null }>
  | Message<'gloss', 'search_failed', { code: string; message: string }>
  | Message<
      'gloss',
      'contents',
      { ref: string | null; title: string; items: number; typed: boolean }
    >
  | Message<'gloss', 'done', { bookId: string }>
  | Message<
      'gloss',
      'tool_calls',
      { text: string; calls: { id: string; name: string; arguments: string }[] }
    >
  | Message<'gloss', 'web_searched', { callId: string; query: string; results: number }>
  | Message<'gloss', 'tool_refused', { callId: string; problem: string }>
  | Message<'gloss', 'recommend', { callId: string; intro: string; books: RecommendedBook[] }>
  | Message<'gloss', 'books_shown', { callId: string; booksCallId: string }>
  | Message<'gloss', 'said', { text: string }>

export type SetupState = 'searching' | 'book_chosen' | 'confirmed'

/** An item of held contents; [ord] is its place in the whole list and [parent] the ord it sits under. */
export type HeldItem = {
  ord: number
  parent: number | null
  depth: number
  number: string | null
  title: string
}

/**
 * The contents a session holds, before any book is made from them: an edition's, named by `ref` and
 * `book`, or a book the reader typed in, named by `title` and `authors` alone.
 */
export type HeldContents = {
  ref: string | null
  /** An edition's source and its page, known from the ref even when the source cannot be asked. */
  source: string | null
  sourceUrl: string | null
  title: string
  authors: string[]
  book: OfferedBook | null
  raw: string
  items: HeldItem[]
}

/** Contents the reader types in: for an edition found without any, or for a book of their own. */
export type TypedContents =
  | { ref: string; toc: string }
  | { title: string; authors: string[]; toc: string }

/** A reader adding a book by talking it through, as GET /api/me/setup-sessions/{id} answers. */
export type SetupSession = {
  id: string
  /** Bumped by every change; each request that changes the session names the one the reader saw. */
  revision: number
  state: SetupState
  /** The latest message is still being answered. */
  searching: boolean
  bookId: string | null
  held: HeldContents | null
  messages: SetupMessage[]
}

export const SETUP_PATH = '/api/me/setup-sessions'

export const sessionPath = (
  id: string,
  step?: 'messages' | 'pick' | 'typed-contents' | 'contents' | 'confirm',
) => `${SETUP_PATH}/${id}${step ? `/${step}` : ''}`

/** The page keeps the session in its address, so a reload or a link comes back to the same thread. */
export const SESSION_PARAM = 's'

export const booksPath = (id: string, seq: number) => `${SETUP_PATH}/${id}/messages/${seq}/books`

export const isSessionId = isBookId

/** What the reader's bubble says; a pick shows the title picked. */
export function readerLine(message: SetupMessage): string | null {
  switch (message.kind) {
    case 'text':
      return message.payload.text
    case 'pick':
      return message.payload.title
    case 'typed':
      return '목차를 직접 입력했어요'
    case 'confirm':
      return '이 목차로 시작할게요'
    default:
      return null
  }
}

/** The live card counts the held contents as edited; earlier cards count what was brought then. */
export function cardItems(session: SetupSession, seq: number, brought: number): number {
  return liveContents(session) === seq && session.held ? session.held.items.length : brought
}

/**
 * The one list asked for as the thread opens: the latest search shown or recommendation. Earlier lists
 * wait until the reader opens them, so a long conversation does not ask the source for every list at once.
 */
export function latestBooks(session: SetupSession): number | null {
  const lists = session.messages.flatMap((message) => {
    const seq = listOf(session.messages, message)
    return seq === null ? [] : [seq]
  })
  return lists.at(-1) ?? null
}

/**
 * The message whose editions a Gloss message shows: a recommendation its own, a list shown the search
 * the model ran in the same turn, a search from before models took part its own. Null for the rest.
 */
export function listOf(messages: SetupMessage[], message: SetupMessage): number | null {
  switch (message.kind) {
    case 'recommend':
      return message.seq
    case 'books':
      return message.payload.callId ? null : message.seq
    case 'books_shown': {
      const { booksCallId } = message.payload
      const found = messages.findLast(
        (other) =>
          other.seq < message.seq && other.kind === 'books' && other.payload.callId === booksCallId,
      )
      return found?.seq ?? null
    }
    default:
      return null
  }
}

/** The model's own steps are shown inside the answer they led to, never as turns of their own. */
export function showsTurn(message: SetupMessage): boolean {
  switch (message.kind) {
    case 'tool_calls':
    case 'web_searched':
    case 'tool_refused':
      return false
    case 'books':
      return !message.payload.callId
    default:
      return true
  }
}

/** A search the model ran on the way to an answer; [seq] is the message that keeps it. */
export type TurnStep = { seq: number; kind: 'search' | 'books'; query: string; found: number }

/** What the model did between the reader's message and the answer at [seq], and how long it took. */
export function turnOf(
  messages: SetupMessage[],
  seq: number,
): { steps: TurnStep[]; seconds: number } | null {
  const end = messages.findIndex((message) => message.seq === seq)
  if (end < 0) return null
  const start = messages.findLastIndex((message, i) => i < end && message.role === 'reader')
  const between = messages.slice(start + 1, end)
  const steps = between.flatMap((message): TurnStep[] => {
    const { seq } = message
    if (message.kind === 'web_searched')
      return [{ seq, kind: 'search', query: message.payload.query, found: message.payload.results }]
    if (message.kind === 'books' && message.payload.callId)
      return [
        { seq, kind: 'books', query: message.payload.query, found: message.payload.books.length },
      ]
    return []
  })
  const answer = messages[end]
  const from = start < 0 ? answer : messages[start]
  if (!answer || !from || !between.some((message) => message.kind === 'tool_calls')) return null
  const seconds = Math.max(
    1,
    Math.round((Date.parse(answer.createdAt) - Date.parse(from.createdAt)) / 1000),
  )
  return { steps, seconds }
}

/** Where a reader fixes what stopped the model: their key, or their monthly limit. */
export function failureLink(code: string): { href: string; label: string } | null {
  if (code === 'llm.no_key' || code === 'llm.key_unreadable' || code === 'llm.key_rejected')
    return { href: '/me#api-key-head', label: 'API 키 등록' }
  if (code === 'budget.limit_not_set' || code === 'budget.limit_reached')
    return { href: '/me#usage', label: '사용 한도' }
  return null
}

/** The host a cited page is on, as its link's short name. */
export function hostOf(url: string): string {
  try {
    return new URL(url).host.replace(/^www\./, '')
  } catch {
    return url
  }
}

/** The chat for one session: a new session, or another one from the address, starts it over. */
export const chatKey = (session: SetupSession | null) => session?.id ?? 'new'

/**
 * Whether a failed start may have made the book all the same: its answer lost on the way, the server
 * failing after it, or another tab starting first. The session is asked again to find out.
 */
export function startMayHaveLanded(result: {
  ok: boolean
  status?: number
  code?: string
}): boolean {
  if (result.ok) return false
  const status = result.status ?? 0
  return status === 0 || status >= 500 || result.code === 'setup.already_confirmed'
}

/**
 * Whether the editor stays open after a save. A save refused because the contents moved on in another
 * tab closes it, so the next save starts from the contents as they are now instead of overwriting them.
 */
export function keepsEditing(result: { ok: boolean; status?: number }): boolean {
  return !result.ok && result.status !== 409
}

/** Editions the reader may pick from any list in the thread, until a book is made: those with contents. */
export const canPick = (state: SetupState, book: OfferedBook) =>
  state !== 'confirmed' && book.hasContents

/** The held items as the tree the book's own contents are shown in. */
export function heldTree(items: HeldItem[]): TocNode[] {
  return treeOf(
    items.map((item) => ({
      id: String(item.ord),
      parentId: item.parent === null ? null : String(item.parent),
      depth: item.depth,
      number: item.number,
      title: item.title,
      itemKey: String(item.ord),
    })),
  )
}

/**
 * The latest contents Gloss brought: the only card that opens the panel while a book is chosen. A
 * new hold is a new message, so this also names the hold, and the panel starts over for each one.
 */
export function liveContents(session: SetupSession): number | null {
  if (session.state !== 'book_chosen') return null
  const last = session.messages.findLast((message) => message.kind === 'contents')
  return last?.seq ?? null
}
