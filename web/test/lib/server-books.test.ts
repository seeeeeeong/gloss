import { describe, expect, it, vi } from 'vitest'
import { bookmarksBeside } from '@/lib/server-books'

const server = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/lib/server-session', () => ({ serverGet: server.get, serverFind: vi.fn() }))

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
