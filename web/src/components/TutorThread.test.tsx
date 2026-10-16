import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { TocItem } from '../lib/books'
import type { ChatMessage } from '../lib/chat'
import { TutorThread } from './TutorThread'

const noop = () => {}
const ITEMS: TocItem[] = [
  { id: 'a', parentId: null, depth: 1, number: '1.1', title: '운영체제가 할 일', itemKey: 'a' },
]

const said = (seq: number, overrides: Partial<ChatMessage> = {}): ChatMessage => ({
  seq,
  role: 'reader',
  turnId: null,
  replyTo: null,
  itemKey: 'a',
  quote: null,
  text: '자원 할당자가 뭔가요?',
  status: null,
  code: null,
  message: null,
  createdAt: '2026-10-17T00:00:00Z',
  ...overrides,
})
const replied = (seq: number, overrides: Partial<ChatMessage> = {}) =>
  said(seq, {
    role: 'gloss',
    replyTo: seq - 1,
    text: '자원을 나눠 주는 역할이에요.',
    status: 'done',
    ...overrides,
  })

function render(props: Partial<Parameters<typeof TutorThread>[0]>) {
  return renderToStaticMarkup(
    <TutorThread
      items={ITEMS}
      messages={[]}
      live={null}
      refused={null}
      loadFailed={false}
      onGo={noop}
      onReload={noop}
      {...props}
    />,
  )
}

describe('TutorThread', () => {
  it('opens with the item named, the quote as the bubble first line, and a copy under the reply', () => {
    const out = render({ messages: [said(1, { quote: '운영체제를 자원 할당자로' }), replied(2)] })
    expect(out).toContain(
      '<button type="button" class="t-div"><span>1.1 운영체제가 할 일</span></button>',
    )
    expect(out).toContain(
      '<div class="t-bub"><span class="t-q">운영체제를 자원 할당자로</span>자원 할당자가 뭔가요?</div>',
    )
    expect(out).toContain('<p>자원을 나눠 주는 역할이에요.</p>')
    expect(out).toContain('aria-label="복사"')
  })

  it('writes the answer under way with the dot cursor', () => {
    const out = render({ live: { asked: said(-1), text: '자원을' } })
    expect(out).toContain('<div class="t-turn gloss" aria-busy="true"><p>자원을<span class="cur"')
    expect(out).not.toContain('답을 받지 못했어요.')
  })

  it('sends a reader without a key to the page that takes one', () => {
    const out = render({
      messages: [said(1), replied(2, { text: '', status: 'failed', code: 'llm.no_key' })],
    })
    expect(out).toContain('튜터에게 물으려면 DeepSeek API 키가 필요해요.<br/>')
    expect(out).toContain('href="/me#api-key-head"')
    expect(out).not.toContain('aria-label="복사"')
  })

  it('says so under a turn that was never answered, and under a turn the server refused', () => {
    const out = render({
      messages: [said(1)],
      refused: {
        code: 'chat.no_plan',
        message: '책의 학습 계획을 만드는 중이에요.',
        text: '질문',
        quote: null,
      },
    })
    expect(out).toContain('<p class="t-note quiet">답을 받지 못했어요.</p>')
    expect(out).toContain(
      '<div class="t-turn gloss" role="alert"><p class="t-note">책의 학습 계획을 만드는 중이에요.</p>',
    )
  })

  it('offers to read the conversation again when it could not be', () => {
    expect(render({ loadFailed: true })).toContain('대화를 불러오지 못했어요.')
  })

  it('shows a turn another tab is waiting on as being answered, not as lost', () => {
    const out = render({ messages: [said(1)], elsewhere: true })
    expect(out).toContain('<p class="t-note quiet pulse">답하는 중</p>')
    expect(out).not.toContain('답을 받지 못했어요.')
  })
})
