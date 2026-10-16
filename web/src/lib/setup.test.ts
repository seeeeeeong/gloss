import { describe, expect, it } from 'vitest'
import {
  booksPath,
  canPick,
  cardItems,
  chatKey,
  heldTree,
  keepsEditing,
  latestBooks,
  liveContents,
  type OfferedBook,
  readerLine,
  type SetupMessage,
  type SetupSession,
  type SetupState,
  sessionPath,
  startMayHaveLanded,
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
