import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { ChatMessage } from '@/features/reader/api/chat'
import { TutorPanel } from '@/features/reader/components/Tutor'

type Panel = Parameters<typeof TutorPanel>[0]
const noop = () => {}

const asked: ChatMessage = {
  seq: 1,
  role: 'reader',
  turnId: 't1',
  replyTo: null,
  itemKey: 's11',
  quote: null,
  text: '커널이 뭐예요?',
  status: null,
  code: null,
  message: null,
  createdAt: '2026-10-18T00:00:00Z',
}

const panel = (messages: ChatMessage[], item: string | null = '1.1 운영체제가 할 일') => {
  const tutor = {
    messages,
    loadFailed: false,
    live: null,
    refused: null,
    elsewhere: false,
    ask: noop,
    stop: noop,
    reload: noop,
    dismissRefusal: noop,
  } as unknown as Panel['tutor']
  return renderToStaticMarkup(
    <TutorPanel
      tutor={tutor}
      items={[]}
      item={item}
      grab={false}
      onGo={noop}
      onClose={noop}
      composer={<form className="composer" />}
    />,
  )
}

describe('TutorPanel', () => {
  it('takes the item open as its title, and its own name before there is one', () => {
    expect(panel([])).toContain('<h2 class="t-title">1.1 운영체제가 할 일</h2>')
    expect(panel([], null)).toContain('<h2 class="t-title">튜터</h2>')
  })

  it('keeps the composer at the foot with the mark alone above it before the first question', () => {
    const out = panel([])
    expect(out).toMatch(/^<div class="tutor">/)
    expect(out).toContain(
      '<div class="t-body"><div class="t-quiet" aria-hidden="true"><span class="brand">gloss<span class="brand-mark">↗</span></span></div></div><div class="t-dock"><form class="composer"></form></div>',
    )
    expect(out).not.toContain('t-thread')
  })

  it('shows the thread over the composer once a question is asked', () => {
    const out = panel([asked])
    expect(out).toContain('<p class="t-qtext">커널이 뭐예요?</p>')
    expect(out).toMatch(/<\/div><div class="t-dock"><form class="composer"><\/form><\/div><\/div>$/)
  })
})
