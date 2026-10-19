import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  booksPath,
  canPick,
  cardItems,
  chatHref,
  chatKey,
  contentsIntro,
  factsOf,
  failureLink,
  type HeldContents,
  type HeldItem,
  type HoldStep,
  hostOf,
  kindTells,
  latestBooks,
  listOf,
  liveContents,
  loadSessions,
  type OfferedBook,
  planPath,
  planProgress,
  planRetryPath,
  planRunning,
  planWaits,
  readerLine,
  readOnly,
  type SetupMessage,
  type SetupSession,
  type SetupSessionSummary,
  type SetupState,
  sessionPath,
  sessionsPath,
  showChatAddress,
  shownHold,
  showsTurn,
  startMayHaveLanded,
  stepOn,
  turnOf,
} from '@/features/setup/api/setup'

const book = (hasContents: boolean): OfferedBook => ({
  ref: '101',
  source: 'yes24',
  url: 'https://www.yes24.com/product/goods/101',
  title: '오브젝트',
  authors: ['조영호 저'],
  publisher: '위키북스',
  publishedOn: '2019-06-17',
  kind: '도서',
  cover: null,
  hasContents,
})

const at = { seq: 1, createdAt: '2026-10-17T00:00:00Z' }

describe('booksPath', () => {
  it('names the search message whose list is asked for again', () => {
    expect(booksPath('a', 2)).toBe('/api/me/setup-sessions/a/messages/2/books')
  })
})

describe('sessionPath', () => {
  it('names the session, and the step when there is one', () => {
    expect(sessionPath('a')).toBe('/api/me/setup-sessions/a')
    expect(sessionPath('a', 'pick')).toBe('/api/me/setup-sessions/a/pick')
  })
})

describe('readerLine', () => {
  it('shows what the reader said, the title they picked, and the style they started in', () => {
    const said: SetupMessage = {
      ...at,
      role: 'reader',
      kind: 'text',
      payload: { text: '오브젝트' },
    }
    const picked: SetupMessage = {
      ...at,
      role: 'reader',
      kind: 'pick',
      payload: { ref: '101', title: '오브젝트' },
    }
    const confirmed: SetupMessage = {
      ...at,
      role: 'reader',
      kind: 'confirm',
      payload: { tocId: 't' },
    }

    expect(readerLine(said)).toBe('오브젝트')
    expect(readerLine(picked)).toBe('오브젝트')
    expect(readerLine(confirmed)).toBe('이 목차로 시작할게요')
    expect(
      readerLine({ ...confirmed, payload: { tocId: 't', level: 'course', voice: 'explain' } }),
    ).toBe('전공 · 설명체')
    expect(readerLine({ ...confirmed, payload: { tocId: 't', level: 'x', voice: 'note' } })).toBe(
      '이 목차로 시작할게요',
    )
    expect(
      readerLine({ ...at, role: 'reader', kind: 'typed', payload: { title: '나의 노트' } }),
    ).toBe('목차를 직접 입력했어요')
  })

  it('has nothing for what Gloss says', () => {
    const done: SetupMessage = { ...at, role: 'gloss', kind: 'done', payload: { bookId: 'b' } }

    expect(readerLine(done)).toBeNull()
  })
})

describe('canPick', () => {
  it('offers an edition with contents from any list until a book is made', () => {
    expect(canPick('searching', book(true))).toBe(true)
    expect(canPick('book_chosen', book(true))).toBe(true)
  })

  it('offers none without contents, or once the book is made', () => {
    expect(canPick('searching', book(false))).toBe(false)
    expect(canPick('confirmed', book(true))).toBe(false)
  })
})

describe('liveContents', () => {
  const contents = (seq: number): SetupMessage => ({
    seq,
    createdAt: at.createdAt,
    role: 'gloss',
    kind: 'contents',
    payload: { ref: null, title: `책 ${seq}`, items: 1, typed: true },
  })
  const session = (state: SetupState): SetupSession => ({
    id: 's',
    revision: 0,
    state,
    searching: false,
    bookId: null,
    held: null,
    messages: [contents(2), contents(4)],
  })

  it('is the latest contents while a book is chosen', () => {
    expect(liveContents(session('book_chosen'))).toBe(4)
  })

  it('tells two typed books apart, though neither has a ref, so the panel starts over for the second', () => {
    const first = { ...session('book_chosen'), messages: [contents(2)] }
    const second = { ...session('book_chosen'), messages: [contents(2), contents(4)] }

    expect(liveContents(first)).not.toBe(liveContents(second))
  })

  it('counts the held contents on the live card, as edited, and what was brought on earlier ones', () => {
    const edited = {
      ...session('book_chosen'),
      held: {
        ref: null,
        source: null,
        sourceUrl: null,
        title: '책 4',
        authors: [],
        book: null,
        raw: '1장\n2장',
        items: [
          { ord: 0, parent: null, depth: 1, number: '1장', title: '1장' },
          { ord: 1, parent: null, depth: 1, number: '2장', title: '2장' },
        ],
      },
    }

    expect(cardItems(edited, 4, 10)).toBe(2)
    expect(cardItems(edited, 2, 10)).toBe(10)
    expect(cardItems(session('searching'), 4, 10)).toBe(10)
  })

  it('is none while searching again or once the book is made', () => {
    expect(liveContents(session('searching'))).toBeNull()
    expect(liveContents(session('confirmed'))).toBeNull()
  })
})

describe('latestBooks', () => {
  it('asks for one list when a conversation with many past searches opens: the latest', () => {
    const searches: SetupMessage[] = Array.from({ length: 9 }, (_, i) => ({
      seq: i * 2 + 2,
      createdAt: at.createdAt,
      role: 'gloss',
      kind: 'books',
      payload: { query: `검색 ${i}`, books: [{ ref: String(i), hasContents: true }] },
    }))
    const opened: SetupSession = {
      id: 's',
      revision: 0,
      state: 'searching',
      searching: false,
      bookId: null,
      held: null,
      messages: searches,
    }

    const asked = searches.filter((message) => message.seq === latestBooks(opened))

    expect(asked.map((message) => message.seq)).toEqual([18])
  })
})

describe('chatKey', () => {
  it('is the session id, so moving between conversations starts the chat over', () => {
    const a: SetupSession = {
      id: 'a',
      revision: 0,
      state: 'searching',
      searching: false,
      bookId: null,
      held: null,
      messages: [],
    }
    const b: SetupSession = { ...a, id: 'b' }

    expect(chatKey(a)).not.toBe(chatKey(b))
    expect(chatKey(null)).toBe('new')
  })
})

describe('chatHref', () => {
  it('gives a conversation its own address under the screen that starts one', () => {
    expect(chatHref('0199a3b2-7c4e-7d10-9b1f-2a3c4d5e6f70')).toBe(
      '/books/new/0199a3b2-7c4e-7d10-9b1f-2a3c4d5e6f70',
    )
  })
})

describe('showChatAddress', () => {
  it('replaces the fresh screen with the conversation, adding no step to go back through', () => {
    const replaced: unknown[][] = []
    showChatAddress('a', { replaceState: (...args: unknown[]) => replaced.push(args) })

    expect(replaced).toEqual([[null, '', '/books/new/a']])
  })
})

describe('readOnly', () => {
  it('only reads a conversation whose book is made, and continues the rest', () => {
    const at = (state: SetupState): SetupSession => ({
      id: 'a',
      revision: 0,
      state,
      searching: false,
      bookId: state === 'confirmed' ? 'b' : null,
      held: null,
      messages: [],
    })

    expect(readOnly(at('confirmed'))).toBe(true)
    expect(readOnly(at('book_chosen'))).toBe(false)
    expect(readOnly(at('searching'))).toBe(false)
    expect(readOnly(null)).toBe(false)
  })
})

describe('sessionsPath', () => {
  it('lists every conversation, or the one book was chosen in', () => {
    expect(sessionsPath()).toBe('/api/me/setup-sessions')
    expect(sessionsPath('b1')).toBe('/api/me/setup-sessions?bookId=b1')
  })
})

describe('loadSessions', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("asks for the reader's conversations and reads them as they come", async () => {
    const listed: SetupSessionSummary[] = [
      {
        id: 'a',
        state: 'confirmed',
        title: '오브젝트',
        bookId: 'b1',
        createdAt: '2026-10-17T00:00:00Z',
        lastActivityAt: '2026-10-17T00:05:00Z',
      },
    ]
    const fetch = vi.fn(async () => new Response(JSON.stringify(listed), { status: 200 }))
    vi.stubGlobal('fetch', fetch)

    const result = await loadSessions()

    expect(fetch).toHaveBeenCalledWith('/api/me/setup-sessions')
    expect(result).toEqual({ ok: true, data: listed })
  })

  it('says why when the server refuses', async () => {
    const refused = { code: 'common.unauthorized', message: '로그인이 필요해요.' }
    vi.stubGlobal('fetch', async () => new Response(JSON.stringify(refused), { status: 401 }))

    expect(await loadSessions()).toEqual({ ok: false, status: 401, ...refused })
  })
})

describe('startMayHaveLanded', () => {
  it('asks again after a lost answer, a server failure or another start that came first', () => {
    expect(startMayHaveLanded({ ok: false, status: 0, code: 'common.network' })).toBe(true)
    expect(startMayHaveLanded({ ok: false, status: 502, code: 'common.unknown' })).toBe(true)
    expect(startMayHaveLanded({ ok: false, status: 409, code: 'setup.already_confirmed' })).toBe(
      true,
    )
  })

  it('takes a clear refusal or a success as it is', () => {
    expect(startMayHaveLanded({ ok: false, status: 409, code: 'setup.held_changed' })).toBe(false)
    expect(startMayHaveLanded({ ok: true })).toBe(false)
  })
})

// A model turn as the server keeps it: the reader's message, the model's calls with what each found,
// then the answer.
const question: SetupMessage = {
  seq: 1,
  createdAt: '2026-10-17T00:00:00Z',
  role: 'reader',
  kind: 'text',
  payload: { text: '운영체제 책 추천' },
}

const searched: SetupMessage = {
  seq: 5,
  createdAt: '2026-10-17T00:00:06Z',
  role: 'gloss',
  kind: 'books',
  payload: { query: '운영체제', callId: 'c2', books: [{ ref: '101', hasContents: true }] },
}

const recommended: SetupMessage = {
  seq: 6,
  createdAt: '2026-10-17T00:00:12Z',
  role: 'gloss',
  kind: 'recommend',
  payload: {
    callId: 'c3',
    intro: '골랐어요.',
    books: [{ ref: '101', reason: '좋아요.', sources: [] }],
  },
}

const turn: SetupMessage[] = [
  question,
  {
    seq: 2,
    createdAt: '2026-10-17T00:00:03Z',
    role: 'gloss',
    kind: 'tool_calls',
    payload: { text: '', calls: [{ id: 'c1', name: 'search_web', arguments: '{}' }] },
  },
  {
    seq: 3,
    createdAt: '2026-10-17T00:00:03Z',
    role: 'gloss',
    kind: 'web_searched',
    payload: { callId: 'c1', query: '운영체제 교재', results: 5 },
  },
  {
    seq: 4,
    createdAt: '2026-10-17T00:00:06Z',
    role: 'gloss',
    kind: 'tool_calls',
    payload: { text: '', calls: [{ id: 'c2', name: 'find_books', arguments: '{}' }] },
  },
  searched,
  recommended,
]

describe('showsTurn', () => {
  it("hides the model's steps and shows its answers and the reader's words", () => {
    expect(turn.filter(showsTurn).map((message) => message.kind)).toEqual(['text', 'recommend'])
  })

  it('still shows a search answered before models took part', () => {
    const old: SetupMessage = {
      ...at,
      role: 'gloss',
      kind: 'books',
      payload: { query: '오브젝트', books: [] },
    }

    expect(showsTurn(old)).toBe(true)
  })
})

describe("an older session's look for contents on other sites", () => {
  const sought: SetupMessage = {
    seq: 3,
    createdAt: '2026-10-17T00:00:00Z',
    role: 'reader',
    kind: 'seek',
    payload: { ref: '101' },
  }
  const look: SetupMessage[] = [
    { ...at, role: 'reader', kind: 'pick', payload: { ref: '101', title: '운영체제' } },
    {
      ...at,
      seq: 2,
      role: 'gloss',
      kind: 'contents',
      payload: { ref: '101', title: '운영체제', items: 11, typed: false },
    },
    sought,
    {
      ...at,
      seq: 4,
      role: 'gloss',
      kind: 'tool_calls',
      payload: { text: '', calls: [{ id: 'c1', name: 'fetch_page', arguments: '{}' }] },
    },
    {
      ...at,
      seq: 5,
      role: 'gloss',
      kind: 'page_fetched',
      payload: {
        callId: 'c1',
        url: 'https://notes.example/toc',
        title: '목차 정리',
        characters: 900,
      },
    },
    {
      seq: 6,
      createdAt: '2026-10-17T00:00:20Z',
      role: 'gloss',
      kind: 'contents',
      payload: { ref: '101', title: '운영체제', items: 206, typed: false },
    },
  ]

  it('shows nothing of the ask or the pages read, and the contents found as contents', () => {
    expect(look.filter(showsTurn).map((message) => message.kind)).toEqual([
      'pick',
      'contents',
      'contents',
    ])
    expect(readerLine(sought)).toBeNull()
    expect(turnOf(look, 6)).toEqual({ seconds: 20, steps: [] })
  })
})

describe('contentsIntro', () => {
  it('says whether the contents were brought or typed in', () => {
    expect(contentsIntro(false)).toBe(
      '목차를 가져왔어요. 살펴보시고 괜찮으면 이 목차로 시작해 주세요.',
    )
    expect(contentsIntro(true)).toBe(
      '목차를 받았어요. 살펴보시고 괜찮으면 이 목차로 시작해 주세요.',
    )
  })
})

describe('turnOf', () => {
  it("collects the searches since the reader's message and how long the answer took", () => {
    expect(turnOf(turn, 6)).toEqual({
      seconds: 12,
      steps: [
        { seq: 3, kind: 'search', query: '운영체제 교재', found: 5 },
        { seq: 5, kind: 'books', query: '운영체제', found: 1 },
      ],
    })
  })

  it('has nothing for an answer the model gave without a tool', () => {
    const said: SetupMessage[] = [
      question,
      { ...at, seq: 2, role: 'gloss', kind: 'said', payload: { text: '어떤 책을 찾으세요?' } },
    ]

    expect(turnOf(said, 2)).toBeNull()
  })
})

describe('listOf', () => {
  it('names the search a list shown ran in its turn, and a recommendation itself', () => {
    const shown: SetupMessage = {
      ...at,
      seq: 7,
      role: 'gloss',
      kind: 'books_shown',
      payload: { callId: 'c4', booksCallId: 'c2' },
    }
    const messages = [...turn, shown]

    expect(listOf(messages, shown)).toBe(5)
    expect(listOf(messages, recommended)).toBe(6)
    expect(listOf(messages, searched)).toBeNull()
    const opened: SetupSession = {
      id: 's',
      revision: 0,
      state: 'searching',
      searching: false,
      bookId: null,
      held: null,
      messages,
    }
    expect(latestBooks(opened)).toBe(5)
    expect(latestBooks({ ...opened, messages: turn })).toBe(6)
  })
})

describe('failureLink', () => {
  it('sends a reader without a usable key to their key, and one at their limit to their usage', () => {
    expect(failureLink('llm.no_key')?.href).toBe('/me#api-key-head')
    expect(failureLink('llm.key_rejected')?.href).toBe('/me#api-key-head')
    expect(failureLink('budget.limit_not_set')?.href).toBe('/me#usage')
    expect(failureLink('budget.limit_reached')?.href).toBe('/me#usage')
    expect(failureLink('llm.provider_busy')).toBeNull()
  })
})

describe('hostOf', () => {
  it('names a cited page by its host', () => {
    expect(hostOf('https://www.example.com/a/b')).toBe('example.com')
    expect(hostOf('not a url')).toBe('not a url')
  })
})

const heldItem = (ord: number, parent: number | null, depth: number, title: string): HeldItem => ({
  ord,
  parent,
  depth,
  number: null,
  title,
})

describe('factsOf', () => {
  const held: HeldContents = {
    ref: '101',
    source: 'yes24',
    sourceUrl: 'https://www.yes24.com/product/goods/101',
    title: '운영체제',
    authors: [],
    book: {
      ...book(true),
      title: '운영체제',
      authors: ['Abraham Silberschatz,Peter Baer Galvin 공저', '박민규 역'],
      publisher: '퍼스트북',
      publishedOn: '2020-02-28',
    },
    raw: '1 서론',
    items: [heldItem(0, null, 0, '1 서론')],
  }

  it('names the authors, the edition and the number of items', () => {
    expect(factsOf(held)).toEqual([
      '저자: Abraham Silberschatz, Peter Baer Galvin 공저, 박민규 역',
      '판본: 퍼스트북 2020',
      '구성: 1개 항목',
    ])
  })

  it('leaves out what a book typed in does not have', () => {
    expect(factsOf({ ...held, book: null, authors: ['김민준'] })).toEqual([
      '저자: 김민준',
      '구성: 1개 항목',
    ])
  })
})

describe('kindTells', () => {
  const paper = book(true)
  const ebook = { ...book(true), ref: '102', kind: 'eBook' }

  it('shows the kind only where it alone tells two editions apart', () => {
    expect(kindTells(paper, [paper, ebook])).toBe(true)
    expect(kindTells(paper, [paper, { ...ebook, publishedOn: '2024-11-11' }])).toBe(false)
    expect(kindTells(paper, [paper])).toBe(false)
  })
})

describe('shownHold', () => {
  const held: HeldContents = {
    ref: '101',
    source: 'yes24',
    sourceUrl: 'https://www.yes24.com/product/goods/101',
    title: '오브젝트',
    authors: [],
    book: null,
    raw: '1장',
    items: [heldItem(0, null, 0, '1장')],
  }
  const contents: SetupMessage = {
    seq: 2,
    createdAt: '2026-10-17T00:00:00Z',
    role: 'gloss',
    kind: 'contents',
    payload: { ref: '101', title: '오브젝트', items: 1, typed: false },
  }
  const session = (state: SetupState, holds: boolean): SetupSession => ({
    id: 's',
    revision: 2,
    state,
    searching: state === 'searching',
    bookId: state === 'confirmed' ? 'b' : null,
    held: holds ? held : null,
    messages: [contents],
  })
  const kept = { seq: 2, held }

  it('shows the contents held now', () => {
    expect(shownHold(session('book_chosen', true), null)).toEqual(kept)
  })

  it('keeps the last contents held once started with', () => {
    expect(shownHold(session('confirmed', false), kept)).toBe(kept)
  })

  it('shows nothing whole once a new message lets the hold go', () => {
    expect(shownHold(session('searching', false), kept)).toBeNull()
  })
})

describe('stepOn', () => {
  const step: HoldStep = {
    seq: 2,
    reviewing: true,
    choosing: true,
    starting: 'note',
    notice: '시작하지 못했어요.',
  }

  it('keeps the step taken on the contents still held', () => {
    expect(stepOn(step, 2)).toBe(step)
  })

  it('drops the editor, a start with its voice and a notice once another book is picked or the hold is let go', () => {
    const none = { reviewing: false, choosing: false, starting: null, notice: null }
    expect(stepOn(step, 4)).toEqual({ seq: 4, ...none })
    expect(stepOn(step, null)).toEqual({ seq: null, ...none })
  })
})

describe('the plan line', () => {
  it('asks the book for its plan, and says how many items it is written for when known', () => {
    expect(planPath('b1')).toBe('/api/me/books/b1/plan')
    expect(planRetryPath('b1')).toBe('/api/me/books/b1/plan/retry')
    expect(planRunning(206)).toBe('206개 항목의 글 순서를 정리하고 있어요.')
    expect(planRunning(null)).toBe('글 순서를 정리하고 있어요.')
  })

  it('looks again until the plan ends, and stops while a look failed until asked again', () => {
    expect(planWaits(null, null)).toBe(true)
    expect(planWaits({ state: 'planning', code: null }, null)).toBe(true)
    expect(planWaits(null, '서버에 연결할 수 없어요.')).toBe(false)
    expect(planWaits({ state: 'failed', code: 'plan.not_started' }, null)).toBe(false)
    expect(planWaits({ state: 'ready', code: null }, null)).toBe(false)
  })

  it('fills from nothing, more slowly for more items, and waits short of the end', () => {
    expect(planProgress(0, 10)).toBe(0)
    expect(planProgress(-5000, 10)).toBe(0)
    expect(planProgress(30_000, 10)).toBeGreaterThan(planProgress(30_000, 200))
    expect(planProgress(30_000, 10)).toBeGreaterThan(planProgress(10_000, 10))
    expect(planProgress(3_600_000, 10)).toBe(95)
  })
})
