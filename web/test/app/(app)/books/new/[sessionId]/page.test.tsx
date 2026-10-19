import { renderToStaticMarkup } from 'react-dom/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SetupSessionPage from '@/app/(app)/books/new/[sessionId]/page'
import type { SetupSession } from '@/features/setup/api/setup'

const NOT_FOUND = 'NEXT_NOT_FOUND'
const server = vi.hoisted(() => ({ sessions: new Map<string, unknown>(), asked: [] as string[] }))

vi.mock('next/navigation', () => ({
  notFound: () => {
    throw new Error('NEXT_NOT_FOUND')
  },
  usePathname: () => '/books/new/x',
  useRouter: () => ({ push: () => {}, refresh: () => {} }),
}))
vi.mock('@/lib/server-session', () => ({
  serverFind: async (path: string) => {
    server.asked.push(path)
    return server.sessions.get(path) ?? null
  },
}))

const ID = '0199a3b2-7c4e-7d10-9b1f-2a3c4d5e6f70'
const at = '2026-10-17T00:00:00Z'

const session = (state: SetupSession['state']): SetupSession => ({
  id: ID,
  revision: 2,
  state,
  searching: false,
  bookId: state === 'confirmed' ? 'b1' : null,
  held: null,
  messages: [
    { seq: 1, role: 'reader', kind: 'text', payload: { text: '오브젝트' }, createdAt: at },
    ...(state === 'confirmed'
      ? [
          {
            seq: 2,
            role: 'gloss' as const,
            kind: 'done' as const,
            payload: { bookId: 'b1', items: 3 },
            createdAt: at,
          },
        ]
      : []),
  ],
})

async function open(sessionId: string) {
  const page = await SetupSessionPage({
    params: Promise.resolve({ sessionId }),
    searchParams: Promise.resolve({}),
  })
  return renderToStaticMarkup(page)
}

describe('the conversation page', () => {
  beforeEach(() => {
    server.sessions.clear()
    server.asked.length = 0
  })

  it('goes on with a conversation not yet started, with its box', async () => {
    server.sessions.set(`/api/me/setup-sessions/${ID}`, session('searching'))

    const out = await open(ID)

    expect(out).toContain('<div class="bubble">오브젝트</div>')
    expect(out).toContain('class="dock"')
  })

  it('only reads a conversation whose book is made, showing the way into it', async () => {
    server.sessions.set(`/api/me/setup-sessions/${ID}`, session('confirmed'))

    const out = await open(ID)

    expect(out).toContain('id="plan-line"')
    expect(out).not.toContain('class="dock"')
  })

  it("is not found for a conversation that is gone or another reader's", async () => {
    await expect(open(ID)).rejects.toThrow(NOT_FOUND)
  })

  it('is not found for an address that names no conversation, without asking', async () => {
    await expect(open('nope')).rejects.toThrow(NOT_FOUND)
    expect(server.asked).toEqual([])
  })
})
