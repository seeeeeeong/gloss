import { describe, expect, it } from 'vitest'
import {
  type ChatMessage,
  itemKeyFor,
  noteOf,
  paragraphsOf,
  quoteOf,
  readEvents,
  threadRowsOf,
} from '@/features/reader/api/chat'
import type { TocItem } from '@/lib/books'

const message = (seq: number, role: 'reader' | 'gloss', itemKey: string | null): ChatMessage => ({
  seq,
  role,
  turnId: null,
  replyTo: role === 'gloss' ? seq - 1 : null,
  itemKey,
  quote: null,
  text: `${role} ${seq}`,
  status: role === 'gloss' ? 'done' : null,
  code: null,
  message: null,
  createdAt: '2026-10-17T00:00:00Z',
})

const item = (itemKey: string, number: string, title: string): TocItem => ({
  id: itemKey,
  parentId: null,
  depth: 1,
  number,
  title,
  itemKey,
})

describe('readEvents', () => {
  it('reads whole events in order and keeps a partial one for the next chunk', () => {
    const asked = JSON.stringify(message(1, 'reader', 'a'))
    const chunk = `event: asked\ndata: ${asked}\n\n: \n\nevent: text\ndata: {"text":"첫\\n줄"}\n\nevent: te`
    const { events, rest } = readEvents(chunk)
    expect(events.map((e) => e.name)).toEqual(['asked', 'text'])
    expect(events[1]).toEqual({ name: 'text', text: '첫\n줄' })
    expect(rest).toBe('event: te')
    const next = readEvents(`${rest}xt\ndata: {"text":"둘"}\n\n`)
    expect(next.events).toEqual([{ name: 'text', text: '둘' }])
    expect(next.rest).toBe('')
  })

  it('skips an event it does not know', () => {
    expect(readEvents('event: later\ndata: {}\n\n').events).toEqual([])
  })
})

describe('quoteOf', () => {
  it('puts a picked phrase on one line, cut with an ellipsis past 280 characters', () => {
    expect(quoteOf('  자원\n  할당자로 ')).toBe('자원 할당자로')
    const long = quoteOf('가'.repeat(300))
    expect(long).toHaveLength(281)
    expect(long.endsWith('…')).toBe(true)
  })
})

describe('threadRowsOf', () => {
  const items = [item('a', '1.1', '운영체제가 할 일'), item('b', '1.2', '컴퓨터 시스템')]

  it('names the item where a turn comes from another one, the first included', () => {
    const rows = threadRowsOf(
      [
        message(1, 'reader', 'a'),
        message(2, 'gloss', 'a'),
        message(3, 'reader', 'a'),
        message(4, 'gloss', 'a'),
        message(5, 'reader', null),
        message(6, 'gloss', null),
        message(7, 'reader', 'b'),
      ],
      items,
    )
    const dividers = rows.flatMap((row) => (row.kind === 'divider' ? [row.label] : []))
    expect(dividers).toEqual(['1.1 운영체제가 할 일', '1.2 컴퓨터 시스템'])
    expect(rows[0]).toMatchObject({ kind: 'divider', itemKey: 'a', known: true })
  })

  it('names an item gone from the contents by its key, without a jump', () => {
    const rows = threadRowsOf([message(1, 'reader', '1장 > 1.9 사라진 절')], items)
    expect(rows[0]).toMatchObject({ kind: 'divider', label: '1.9 사라진 절', known: false })
  })
})

describe('noteOf', () => {
  const reply = (status: ChatMessage['status'], code: string | null, text: string | null = null) =>
    noteOf({ status, code, message: text })

  it('says nothing under an answer that finished', () => {
    expect(reply('done', null)).toBeNull()
  })

  it('sends a reader without a key or past their limit to the page that fixes it', () => {
    expect(reply('failed', 'llm.no_key')).toEqual({
      text: '튜터에게 물으려면 DeepSeek API 키가 필요해요.',
      fix: 'key',
      quiet: false,
    })
    expect(reply('failed', 'budget.limit_reached')?.fix).toBe('usage')
    expect(reply('failed', 'llm.key_rejected', 'DeepSeek이 API 키를 받지 않았습니다')?.fix).toBe(
      'key',
    )
  })

  it('tells any other failure in the server words, and a stop or a cut quietly', () => {
    expect(reply('failed', 'budget.too_frequent', '요청이 너무 잦아요.')).toEqual({
      text: '요청이 너무 잦아요.',
      fix: null,
      quiet: false,
    })
    expect(reply('stopped', null)?.quiet).toBe(true)
    expect(reply('cut', 'length')?.text).toBe('답이 중간에 끊겼어요.')
  })
})

describe('itemKeyFor', () => {
  const items = [item('a', '1.1', '운영체제가 할 일')]
  const asked = [message(1, 'reader', 'gone'), message(2, 'gloss', 'gone')]

  it('asks from the picked phrase, else from the item being read', () => {
    expect(itemKeyFor({ text: '구절', itemKey: 'a' }, 'b', asked, items)).toBe('a')
    expect(itemKeyFor(null, 'b', asked, items)).toBe('b')
  })

  it('follows the latest turn from the cover only while the contents still hold its item', () => {
    expect(itemKeyFor(null, null, [message(1, 'reader', 'a')], items)).toBe('a')
    expect(itemKeyFor(null, null, asked, items)).toBeNull()
  })
})

describe('paragraphsOf', () => {
  it('splits at blank lines, keeps a single line break, and drops empty ones', () => {
    expect(paragraphsOf('첫 문단\n\n둘째\n줄')).toEqual([
      { at: 0, text: '첫 문단' },
      { at: 6, text: '둘째\n줄' },
    ])
    expect(paragraphsOf('가\n  \n\n  - 나\n\n')).toEqual([
      { at: 0, text: '가' },
      { at: 6, text: '  - 나' },
    ])
    expect(paragraphsOf('')).toEqual([{ at: 0, text: '' }])
  })
})
