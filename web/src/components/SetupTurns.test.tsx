import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { OfferedBook, SetupMessage, SetupSession } from '../lib/setup'
import { BookRow, GlossTurn, MorePages } from './SetupTurns'

const noop = () => {}

const at = { seq: 2, createdAt: '2026-10-17T00:00:02Z' } as const

const book: OfferedBook = {
  ref: '101',
  source: 'yes24',
  url: 'https://www.yes24.com/product/goods/101',
  title: '운영체제',
  authors: ['Silberschatz 저'],
  publisher: '퍼스트북',
  publishedOn: '2020-02-28',
  kind: '도서',
  cover: null,
  hasContents: true,
}

const asked: SetupMessage = {
  seq: 1,
  createdAt: '2026-10-17T00:00:00Z',
  role: 'reader',
  kind: 'text',
  payload: { text: '운영체제 책' },
}

const found: SetupMessage = {
  seq: 3,
  createdAt: '2026-10-17T00:00:04Z',
  role: 'gloss',
  kind: 'books',
  payload: { query: '운영체제', callId: 'c1', books: [{ ref: '101', hasContents: true }] },
}

const recommended: SetupMessage = {
  seq: 4,
  createdAt: '2026-10-17T00:00:09Z',
  role: 'gloss',
  kind: 'recommend',
  payload: {
    callId: 'c2',
    intro: '전공 교재부터 입문서까지 골랐어요.',
    books: [{ ref: '101', reason: '전공 수업에서 많이 써요.', sources: [] }],
  },
}

const messages: SetupMessage[] = [
  asked,
  {
    seq: 2,
    createdAt: '2026-10-17T00:00:04Z',
    role: 'gloss',
    kind: 'tool_calls',
    payload: { text: '', calls: [{ id: 'c1', name: 'find_books', arguments: '{}' }] },
  },
  found,
  recommended,
]

const session: SetupSession = {
  id: 's',
  revision: 3,
  state: 'searching',
  searching: false,
  bookId: null,
  held: null,
  messages,
}

const turn = (message: SetupMessage) =>
  renderToStaticMarkup(
    <GlossTurn
      message={message}
      session={{ ...session, messages: [asked, message] }}
      busy={false}
      panel={false}
      onPick={noop}
      onType={noop}
      onPanel={noop}
    />,
  )

describe('BookRow', () => {
  it('shows a recommended book by its rank, title, one meta line, its reason and the pages cited', () => {
    const out = renderToStaticMarkup(
      <BookRow
        book={book}
        state="searching"
        busy={false}
        onPick={noop}
        onType={noop}
        rank={1}
        reason="전공 수업에서 많이 써요."
        sources={[
          { url: 'https://os-book.com/ten', title: 'OSC 10판 공식 사이트' },
          { url: 'https://www.example.org/review', title: '교재 비교' },
          { url: 'https://blog.example.net/os', title: '공룡책 목차 정리' },
        ]}
      />,
    )

    expect(out).toContain('1. <a class="dl" href="https://www.yes24.com/product/goods/101"')
    expect(out).toContain('<p class="bk-meta">Silberschatz 저 · 퍼스트북 · 2020 · 도서</p>')
    expect(out).toContain('<p class="bk-why">전공 수업에서 많이 써요.</p>')
    expect(out).toContain('<span>OSC 10판 공식 사이트</span>')
    expect(out).toContain('href="https://os-book.com/ten"')
    expect(out).toMatch(
      /<button type="button" class="spill" aria-expanded="false"[^>]*>os-book\.com(<!-- -->)? \+(<!-- -->)?2<\/button>/,
    )
    expect(out).toContain('aria-label="출처 3곳: os-book.com, example.org, blog.example.net"')
    expect(out).toContain('aria-label="운영체제, 이 책으로 학습"')
  })

  it('offers a book without contents to be typed in, and no rank or reason in a plain list', () => {
    const out = renderToStaticMarkup(
      <BookRow
        book={{ ...book, hasContents: false }}
        state="searching"
        busy={false}
        onPick={noop}
        onType={noop}
      />,
    )

    expect(out).toContain('직접 입력')
    expect(out).not.toContain('이 책으로 학습')
    expect(out).not.toContain('bk-why')
    expect(out).not.toContain('bk-src')
  })
})

describe('MorePages', () => {
  it('opens every cited page past the first as a link, by its title and host', () => {
    const out = renderToStaticMarkup(
      <MorePages
        pages={[
          { url: 'https://www.example.org/review', title: '교재 비교' },
          { url: 'https://blog.example.net/os', title: '공룡책 목차 정리' },
        ]}
      />,
    )

    expect(out).toContain('href="https://www.example.org/review"')
    expect(out).toContain('href="https://blog.example.net/os"')
    expect(out).toContain(
      '<span class="src-title">공룡책 목차 정리</span><span class="src-host">blog.example.net</span>',
    )
    expect(out.match(/target="_blank" rel="noopener noreferrer"/g)).toHaveLength(2)
  })
})

describe('GlossTurn', () => {
  it('opens a recommendation with how long it took and its intro, then its list', () => {
    const out = renderToStaticMarkup(
      <GlossTurn
        message={recommended}
        session={session}
        busy={false}
        panel={false}
        onPick={noop}
        onType={noop}
        onPanel={noop}
      />,
    )

    expect(out).toContain('9초 동안 처리함')
    expect(out).toContain('aria-expanded="false"')
    expect(out).toContain('<p>전공 교재부터 입문서까지 골랐어요.</p>')
    expect(out).toContain('불러오는 중')
  })

  it('shows what the model said as plain paragraphs, never as markup', () => {
    const out = turn({
      ...at,
      role: 'gloss',
      kind: 'said',
      payload: { text: '어떤 수준인가요?\n\n<b>처음</b>이면 입문서를 권해요.' },
    })

    expect(out).toBe('<p>어떤 수준인가요?</p><p>&lt;b&gt;처음&lt;/b&gt;이면 입문서를 권해요.</p>')
  })

  it('links a reader without a key to their key, and one without a limit to their usage', () => {
    const failed = (code: string, message: string) =>
      turn({
        ...at,
        role: 'gloss',
        kind: 'search_failed',
        payload: { code, message },
      })

    expect(failed('llm.no_key', '키를 먼저 등록해 주세요')).toContain('href="/me#api-key-head"')
    expect(failed('budget.limit_not_set', '한도를 먼저 정해 주세요')).toContain('href="/me#usage"')
    expect(failed('setup.no_answer', '다시 보내 주세요')).not.toContain('href')
  })

  it('shows nothing of a search the model ran: it is a step of the answer', () => {
    expect(turn(found)).toBe('')
  })
})
