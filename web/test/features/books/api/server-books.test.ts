import { describe, expect, it, vi } from 'vitest'
import { bookmarksBeside, lastReadOf, recentBeside } from '@/features/books/api/server-books'

const server = vi.hoisted(() => ({ get: vi.fn(), find: vi.fn() }))
vi.mock('@/lib/server-session', () => ({ serverGet: server.get, serverFind: server.find }))

describe('bookmarksBeside', () => {
  it('shows none when the server cannot list them, so the page beside them still opens', async () => {
    server.get.mockRejectedValueOnce(new Error('GET /api/me/bookmarks answered 500'))
    await expect(bookmarksBeside()).resolves.toEqual([])
  })

  it('still sends a lost session to sign-in', async () => {
    const toSignIn = Object.assign(new Error('NEXT_REDIRECT'), {
      digest: 'NEXT_REDIRECT;replace;/login;307;',
    })
    server.get.mockRejectedValueOnce(toSignIn)
    await expect(bookmarksBeside()).rejects.toBe(toSignIn)
  })
})

describe('recentBeside', () => {
  it('shows none when the server cannot list them, so the page beside them still opens', async () => {
    server.get.mockRejectedValueOnce(new Error('GET /api/me/books/recent answered 500'))
    await expect(recentBeside()).resolves.toEqual({ books: [], chats: [] })
  })
})

describe('lastReadOf', () => {
  it('opens the book at its first item when the server cannot say where the reader was', async () => {
    server.find.mockRejectedValueOnce(new Error('GET /api/me/books/a/read answered 500'))
    await expect(lastReadOf('a')).resolves.toBeNull()
  })
})
