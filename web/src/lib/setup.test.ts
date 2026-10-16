import { describe, expect, it } from 'vitest'
import {
  booksPath,
  canPick,
  cardItems,
  chatKey,
  failureLink,
  heldTree,
  hostOf,
  keepsEditing,
  latestBooks,
  listOf,
  liveContents,
  type OfferedBook,
  readerLine,
  type SetupMessage,
  type SetupSession,
  type SetupState,
  sessionPath,
  showsTurn,
  startMayHaveLanded,
  turnOf,
} from './setup'

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
  it('shows what the reader said, the title they picked, and their start', () => {
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

describe('heldTree', () => {
  it('nests each item under the one its parent names', () => {
    const tree = heldTree([
      { ord: 0, parent: null, depth: 1, number: '1장', title: '객체' },
      { ord: 1, parent: 0, depth: 2, number: '1.1', title: '티켓' },
      { ord: 2, parent: null, depth: 1, number: '2장', title: '설계' },
    ])

    expect(tree.map((node) => [node.title, node.children.map((child) => child.title)])).toEqual([
      ['객체', ['티켓']],
      ['설계', []],
    ])
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

describe('keepsEditing', () => {
  it('closes the editor once saved, or when another tab changed the contents first', () => {
    expect(keepsEditing({ ok: true })).toBe(false)
    expect(keepsEditing({ ok: false, status: 409 })).toBe(false)
  })
  it('keeps the edit open after a refusal the reader can fix in place', () => {
    expect(keepsEditing({ ok: false, status: 400 })).toBe(true)
    expect(keepsEditing({ ok: false, status: 0 })).toBe(true)
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
