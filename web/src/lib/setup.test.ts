import { describe, expect, it } from 'vitest'
import {
  booksPath,
  canPick,
  cardItems,
  chatKey,
  contentsHeading,
  docOf,
  factsOf,
  failureLink,
  type HeldContents,
  type HeldItem,
  heldTree,
  hostOf,
  keepsEditing,
  kindTells,
  latestBooks,
  listOf,
  liveContents,
  type OfferedBook,
  readerLine,
  type SetupMessage,
  type SetupSession,
  type SetupState,
  sessionPath,
  shownHold,
  showsTurn,
  startMayHaveLanded,
  stepOn,
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

const heldItem = (ord: number, parent: number | null, depth: number, title: string): HeldItem => ({
  ord,
  parent,
  depth,
  number: null,
  title,
})

describe('docOf', () => {
  it('reads three levels as parts, chapters and lists of sections, deeper ones nested', () => {
    const blocks = docOf([
      heldItem(0, null, 0, 'Part 1 개관'),
      heldItem(1, 0, 1, '1 서론'),
      heldItem(2, 1, 2, '1.1 운영체제가 할 일'),
      heldItem(3, 2, 3, '1.1.1 사용자 관점'),
      heldItem(4, 1, 2, '1.2 구성'),
      heldItem(5, 0, 1, '2 구조'),
    ])

    expect(blocks).toEqual([
      { kind: 'part', key: '0', label: 'Part 1 개관' },
      { kind: 'chapter', key: '1', label: '1 서론' },
      {
        kind: 'list',
        key: '2',
        items: [
          {
            key: '2',
            label: '1.1 운영체제가 할 일',
            children: [{ key: '3', label: '1.1.1 사용자 관점', children: [] }],
          },
          { key: '4', label: '1.2 구성', children: [] },
        ],
      },
      { kind: 'chapter', key: '5', label: '2 구조' },
    ])
  })

  it('reads two levels as chapters and their sections, one as a single list', () => {
    expect(
      docOf([heldItem(0, null, 0, '1장'), heldItem(1, 0, 1, '1.1'), heldItem(2, null, 0, '2장')]),
    ).toEqual([
      { kind: 'chapter', key: '0', label: '1장' },
      { kind: 'list', key: '1', items: [{ key: '1', label: '1.1', children: [] }] },
      { kind: 'chapter', key: '2', label: '2장' },
    ])
    expect(docOf([heldItem(0, null, 0, '1장'), heldItem(1, null, 0, '2장')])).toEqual([
      {
        kind: 'list',
        key: '0',
        items: [
          { key: '0', label: '1장', children: [] },
          { key: '1', label: '2장', children: [] },
        ],
      },
    ])
  })
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
    expect(contentsHeading(held)).toBe('운영체제 전체 목차')
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
  const step = { seq: 2, editing: true, starting: true, notice: '시작하지 못했어요.' }

  it('keeps the step taken on the contents still held', () => {
    expect(stepOn(step, 2)).toBe(step)
  })

  it('drops the editor, a start and a notice once another book is picked or the hold is let go', () => {
    const none = { editing: false, starting: false, notice: null }
    expect(stepOn(step, 4)).toEqual({ seq: 4, ...none })
    expect(stepOn(step, null)).toEqual({ seq: null, ...none })
  })
})
