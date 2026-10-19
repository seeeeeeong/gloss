import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { SetupMessage, SetupSession } from '@/features/setup/api/setup'
import { SetupChat } from '@/features/setup/views/SetupChat'

vi.mock('next/navigation', () => ({
  usePathname: () => '/books/new',
  useRouter: () => ({ push: () => {}, refresh: () => {} }),
}))

const at = '2026-10-17T00:00:00Z'

const session: SetupSession = {
  id: 's',
  revision: 1,
  state: 'searching',
  searching: false,
  bookId: null,
  held: null,
  messages: [],
}

const started: SetupMessage[] = [
  { seq: 1, role: 'reader', kind: 'text', payload: { text: '오브젝트' }, createdAt: at },
  {
    seq: 2,
    role: 'reader',
    kind: 'pick',
    payload: { ref: '101', title: '오브젝트' },
    createdAt: at,
  },
  {
    seq: 3,
    role: 'gloss',
    kind: 'contents',
    payload: { ref: '101', title: '오브젝트', items: 3, typed: false },
    createdAt: at,
  },
  {
    seq: 4,
    role: 'reader',
    kind: 'confirm',
    payload: { tocId: 't', level: 'work', voice: 'explain' },
    createdAt: at,
  },
  { seq: 5, role: 'gloss', kind: 'done', payload: { bookId: 'b1', items: 3 }, createdAt: at },
]

describe('SetupChat', () => {
  it('asks in the box on the empty screen, and leaves the box in the thread bare', () => {
    const empty = renderToStaticMarkup(<SetupChat initial={null} />)
    const thread = renderToStaticMarkup(<SetupChat initial={session} />)

    expect(empty).toContain('placeholder="Gloss에게 물어보세요"')
    expect(thread).toContain('class="dock"')
    expect(thread).not.toContain('placeholder')
  })

  it('reads a conversation whose book is made as it was, with its plan and no box', () => {
    const confirmed = { ...session, state: 'confirmed' as const, bookId: 'b1', messages: started }

    const out = renderToStaticMarkup(<SetupChat initial={confirmed} />)

    expect(out).toContain('<div class="bubble">오브젝트</div>')
    expect(out).toContain('id="plan-line"')
    expect(out).not.toContain('class="dock"')
    expect(out).not.toContain('<textarea')
  })

  it('goes on with a conversation whose book is not made yet', () => {
    const chosen = { ...session, state: 'book_chosen' as const, messages: started.slice(0, 3) }

    const out = renderToStaticMarkup(<SetupChat initial={chosen} />)

    expect(out).toContain('class="dock"')
    expect(out).toContain('<textarea')
    expect(out).not.toContain('id="plan-line"')
  })
})
