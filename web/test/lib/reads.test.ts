import { afterEach, describe, expect, it, vi } from 'vitest'
import { chatHref, chatsByBook, loadRecent, recentChatsPath } from '@/lib/reads'

const chat = (id: string, bookId: string) => ({
  id,
  bookId,
  firstQuestion: '커널이 뭐예요?',
  lastActivityAt: '2026-10-20T00:00:00Z',
})

const book = (bookId: string) => ({
  bookId,
  title: '운영체제',
  itemKey: '1 > 1.1',
  itemNumber: '1.1',
  itemTitle: '프로세스',
  readAt: '2026-10-20T00:00:00Z',
})

const answer = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })

afterEach(() => vi.unstubAllGlobals())

describe('chatsByBook', () => {
  it("groups the conversations by book, keeping each book's newest first", () => {
    const grouped = chatsByBook([chat('s3', 'a'), chat('s2', 'b'), chat('s1', 'a')])
    expect(grouped.get('a')?.map((it) => it.id)).toEqual(['s3', 's1'])
    expect(grouped.get('b')?.map((it) => it.id)).toEqual(['s2'])
    expect(grouped.get('c')).toBeUndefined()
  })
})

describe('paths', () => {
  it('names the books in one query, and the conversation the reader opens with', () => {
    expect(recentChatsPath(['a', 'b'])).toBe('/api/me/chat/recent?bookIds=a,b')
    expect(chatHref('a', 's1')).toBe('/books/a?chat=s1')
  })
})

describe('loadRecent', () => {
  it('reads the books, then the conversations about them', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(answer([book('a')]))
      .mockResolvedValueOnce(answer([chat('s1', 'a')]))
    vi.stubGlobal('fetch', fetch)

    await expect(loadRecent()).resolves.toEqual({ books: [book('a')], chats: [chat('s1', 'a')] })
    expect(fetch.mock.calls.map((call) => call[0])).toEqual([
      '/api/me/books/recent',
      '/api/me/chat/recent?bookIds=a',
    ])
  })

  it('asks for no conversations before a book is read, and gives up when either read fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(answer([])))
    await expect(loadRecent()).resolves.toEqual({ books: [], chats: [] })

    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(answer([book('a')]))
        .mockResolvedValueOnce(answer({ code: 'x', message: 'y' }, 500)),
    )
    await expect(loadRecent()).resolves.toBeNull()
  })
})
