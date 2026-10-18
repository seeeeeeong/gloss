import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { SetupSession } from '@/features/setup/api/setup'
import { SetupChat } from '@/features/setup/views/SetupChat'

vi.mock('next/navigation', () => ({ useSearchParams: () => new URLSearchParams() }))

const session: SetupSession = {
  id: 's',
  revision: 1,
  state: 'searching',
  searching: false,
  bookId: null,
  held: null,
  messages: [],
}

describe('SetupChat', () => {
  it('asks in the box on the empty screen, and leaves the box in the thread bare', () => {
    const empty = renderToStaticMarkup(<SetupChat initial={null} />)
    const thread = renderToStaticMarkup(<SetupChat initial={session} />)

    expect(empty).toContain('placeholder="Gloss에게 물어보세요"')
    expect(thread).toContain('class="dock"')
    expect(thread).not.toContain('placeholder')
  })
})
