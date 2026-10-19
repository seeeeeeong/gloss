import { describe, expect, it, vi } from 'vitest'
import { chatOfBook } from '@/features/setup/api/server-setup'

const server = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/lib/server-session', () => ({ serverGet: server.get }))

describe('chatOfBook', () => {
  it("leads to the latest conversation that made the book, asking for that book's alone", async () => {
    server.get.mockResolvedValueOnce([{ id: 's2' }, { id: 's1' }])
    await expect(chatOfBook('b1')).resolves.toBe('/books/new/s2')
    expect(server.get).toHaveBeenLastCalledWith('/api/me/setup-sessions?bookId=b1')
  })

  it('leads nowhere for a book chosen in none', async () => {
    server.get.mockResolvedValueOnce([])
    await expect(chatOfBook('b1')).resolves.toBeNull()
  })

  it('leads nowhere when the server cannot say, so the book still opens', async () => {
    server.get.mockRejectedValueOnce(new Error('GET answered 500'))
    await expect(chatOfBook('b1')).resolves.toBeNull()
  })

  it('still sends a lost session to sign-in', async () => {
    const toSignIn = Object.assign(new Error('NEXT_REDIRECT'), {
      digest: 'NEXT_REDIRECT;replace;/login;307;',
    })
    server.get.mockRejectedValueOnce(toSignIn)
    await expect(chatOfBook('b1')).rejects.toBe(toSignIn)
  })
})
