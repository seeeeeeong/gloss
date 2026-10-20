import { type ApiResult, load } from '@/lib/api'
import { authorsLine, type BookCandidate, editionOf, editionParts, isBookId } from '@/lib/books'
import { type Level, levelLabel, type Voice, voiceLabel } from '@/lib/styles'

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
  | Message<'reader', 'confirm', { tocId: string; level?: string | null; voice?: string | null }>
  // Only older sessions hold this and page_fetched, from a search for contents on other sites.
  | Message<'reader', 'seek', { ref: string }>
  | Message<'gloss', 'books', { query: string; books: FoundRef[]; callId?: string | null }>
  | Message<'gloss', 'search_failed', { code: string; message: string }>
  | Message<
      'gloss',
      'contents',
      { ref: string | null; title: string; items: number; typed: boolean }
    >
  | Message<'gloss', 'done', { bookId: string; items?: number | null }>
  | Message<
      'gloss',
      'tool_calls',
      { text: string; calls: { id: string; name: string; arguments: string }[] }
    >
  | Message<'gloss', 'web_searched', { callId: string; query: string; results: number }>
  | Message<
      'gloss',
      'page_fetched',
      { callId: string; url: string; title: string; characters: number }
    >
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

/** Where a new conversation starts. */
export const NEW_CHAT = '/books/new'

/** A conversation's own address, so a reload, a return or a link comes back to the same thread. */
export const chatHref = (id: string) => `${NEW_CHAT}/${id}`

/** Where a conversation once sat, in the query of the screen that starts one; read only to send it on. */
export const SESSION_PARAM = 's'

/**
 * Puts a conversation just started in the address without a reload, so a refresh or coming back
 * reopens it rather than a fresh screen.
 */
export function showChatAddress(
  id: string,
  history: Pick<History, 'replaceState'> = window.history,
) {
  history.replaceState(null, '', chatHref(id))
}

/** One of the reader's conversations, as GET /api/me/setup-sessions lists them. */
export type SetupSessionSummary = {
  id: string
  state: SetupState
  /** The book held or made, else what the reader first asked. */
  title: string
  bookId: string | null
  createdAt: string
  lastActivityAt: string
}

/** The reader's conversations they wrote in, latest activity first; one book's alone when named. */
export const sessionsPath = (bookId?: string) =>
  bookId ? `${SETUP_PATH}?bookId=${encodeURIComponent(bookId)}` : SETUP_PATH

export const loadSessions = (): Promise<ApiResult<SetupSessionSummary[]>> =>
  load<SetupSessionSummary[]>(sessionsPath())

/** A book's conversation is only read: the thread as it was, with no way to add to it. */
export const readOnly = (session: SetupSession | null) => session?.state === 'confirmed'

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
      return styleLine(message.payload) ?? '이 목차로 시작할게요'
    default:
      return null
  }
}

/** The level and voice a start chose, as the reader's bubble says them; none before the choice. */
function styleLine(chosen: { level?: string | null; voice?: string | null }): string | null {
  const level = chosen.level ? levelLabel(chosen.level) : null
  const voice = chosen.voice ? voiceLabel(chosen.voice) : null
  return level && voice ? `${level} · ${voice}` : null
}

/** How the book's posts are to be written, sent with the start. */
export type ChosenStyle = { level: Level; voice: Voice }

/** The live contents count as edited; earlier ones count what was brought then. */
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

/**
 * The model's own steps are shown inside the answer they led to, never as turns of their own; an older
 * session's ask for contents on other sites is not shown at all.
 */
export function showsTurn(message: SetupMessage): boolean {
  switch (message.kind) {
    case 'tool_calls':
    case 'web_searched':
    case 'page_fetched':
    case 'tool_refused':
    case 'seek':
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

/** Editions the reader may pick from any list in the thread, until a book is made: those with contents. */
export const canPick = (state: SetupState, book: OfferedBook) =>
  state !== 'confirmed' && book.hasContents

/**
 * The latest contents Gloss brought: the only ones started with while a book is chosen. A new hold is
 * a new message, so this also names the hold, and its panel starts over for each one.
 */
export function liveContents(session: SetupSession): number | null {
  if (session.state !== 'book_chosen') return null
  const last = session.messages.findLast((message) => message.kind === 'contents')
  return last?.seq ?? null
}

export const contentsIntro = (typed: boolean) =>
  `${typed ? '목차를 받았어요.' : '목차를 가져왔어요.'} 살펴보시고 괜찮으면 이 목차로 시작해 주세요.`

export const itemsCount = (count: number) => `${count}개 항목`

/** The facts under the book's title: who wrote it, which edition, and how many items. */
export function factsOf(held: HeldContents): string[] {
  const authors = authorsLine(held.book?.authors ?? held.authors)
  const edition = held.book ? editionParts(held.book).join(' ') : ''
  return [
    authors && `저자: ${authors}`,
    edition && `판본: ${edition}`,
    `구성: ${itemsCount(held.items.length)}`,
  ].filter((fact) => fact.length > 0)
}

/** The kind (도서, eBook) only where it alone tells two editions of one list apart. */
export function kindTells(book: OfferedBook, books: OfferedBook[]): boolean {
  const edition = editionOf(book, false)
  return books.some(
    (other) =>
      other.ref !== book.ref &&
      other.title === book.title &&
      other.kind !== book.kind &&
      editionOf(other, false) === edition,
  )
}

/** Contents shown whole in the thread, by the message that brought them. */
export type ShownHold = { seq: number; held: HeldContents }

/**
 * The contents held now, else the last ones held once a book is made from them, which the session
 * no longer holds. A hold let go by a new message shows nothing whole.
 */
export function shownHold(session: SetupSession | null, kept: ShownHold | null): ShownHold | null {
  const live = session ? liveContents(session) : null
  if (live !== null && session?.held) return { seq: live, held: session.held }
  return session?.state === 'confirmed' ? kept : null
}

/** What the reader is doing with one hold: reading it in the panel, starting with it, why the last try failed. */
export type HoldStep = {
  seq: number | null
  reviewing: boolean
  /** The reader chose these contents and is asked how the posts are written. */
  choosing: boolean
  /** The voice a start is on its way with. */
  starting: Voice | null
  notice: string | null
}

/**
 * The step as it stands for the contents held at [live]. A step taken on other contents, or on a
 * hold let go, is over: nothing of it (the editor, a start under way, a notice) carries over.
 */
export function stepOn(step: HoldStep, live: number | null): HoldStep {
  if (step.seq !== null && step.seq === live) return step
  return { seq: live, reviewing: false, choosing: false, starting: null, notice: null }
}

/** How far a book's plan has come, as GET /api/me/books/{id}/plan answers. */
export type PlanStatus = { state: 'planning' | 'ready' | 'failed'; code: string | null }

export const planPath = (bookId: string) => `/api/me/books/${bookId}/plan`

/** Whether the plan is still looked at: until it ends, and not while the last look failed. */
export const planWaits = (status: PlanStatus | null, lookFailed: string | null) =>
  (status === null || status.state === 'planning') && lookFailed === null

/** Asks again for a plan that ended without one; a plan kept or still to come is left as it is. */
export const planRetryPath = (bookId: string) => `${planPath(bookId)}/retry`

export const planRunning = (items: number | null | undefined) =>
  `${items ? `${items}개 항목의 ` : ''}글 순서를 정리하고 있어요.`

/**
 * The thin line's fill while the plan is written, from how long it has run. The answer grows with the
 * items, so the time expected does too; the line nears its end and waits there rather than claim a
 * time it cannot know.
 */
export function planProgress(elapsedMs: number, items: number | null | undefined): number {
  const expectedMs = (10 + 2 * (items ?? 100)) * 1000
  const share = 1 - Math.exp(-Math.max(0, elapsedMs) / expectedMs)
  return Math.round(Math.min(0.95, share) * 100)
}
