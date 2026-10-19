import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { ChatSession } from '@/features/reader/api/chat'
import { DeleteChatDialog, TutorHistory } from '@/features/reader/components/TutorHistory'
import type { TocItem } from '@/lib/books'

const noop = () => {}

const ITEMS: TocItem[] = [
  { id: 'a', parentId: null, depth: 1, number: '6.2', title: '지역성', itemKey: 'a' },
]

const session = (id: string, lastActivityAt: string, itemKey: string | null): ChatSession => ({
  id,
  firstQuestion: `${id}: 지역성이 왜 캐시에 중요해?`,
  itemKey,
  itemKeys: itemKey ? [itemKey] : [],
  startedAt: lastActivityAt,
  lastActivityAt,
})

const history = (sessions: ChatSession[], openId: string | null, failed: string | null = null) =>
  renderToStaticMarkup(
    <TutorHistory
      sessions={sessions}
      items={ITEMS}
      openId={openId}
      now={new Date(2026, 9, 18, 12)}
      failed={failed}
      onOpen={noop}
      onDelete={noop}
    />,
  )

describe('TutorHistory', () => {
  const older = session('s1', new Date(2026, 9, 12, 9).toISOString(), 'a')
  const newer = session('s2', new Date(2026, 9, 18, 8).toISOString(), null)

  it('lists newest first, each by its first question, the item it started on and the day', () => {
    const out = history([older, newer], null)
    expect(out.indexOf('s2:')).toBeLessThan(out.indexOf('s1:'))
    expect(out).toContain(
      '<span class="h-m"><span class="it"><span class="no">6.2</span>지역성</span><span class="d">10월 12일</span></span>',
    )
    expect(out).toContain('<span class="h-m"><span class="d">오늘</span></span>')
  })

  it('marks the conversation open, and only it', () => {
    const out = history([older, newer], 's1')
    expect(out.match(/aria-current="true"/g)).toHaveLength(1)
    expect(out).toMatch(
      /<li class="h-row" aria-current="true"><button type="button" class="h-open"><span class="h-q">s1:/,
    )
  })

  it('gives each row a trash button, and says why a delete failed', () => {
    const out = history([older], null, '답하는 중인 대화예요.')
    expect(out).toContain('aria-label="대화 삭제"')
    expect(out).toContain('<p class="t-note h-failed">답하는 중인 대화예요.</p>')
  })
})

describe('DeleteChatDialog', () => {
  const out = renderToStaticMarkup(<DeleteChatDialog onDelete={noop} onCancel={noop} />)

  it('asks with its title alone, without a close button or a description', () => {
    expect(out).toContain('<h2 id="delete-chat-title">대화를 삭제할까요?</h2>')
    expect(out).not.toContain('class="ibtn x"')
    expect(out).not.toContain('dlg-p')
    expect(out).toContain('class="dlg confirm"')
  })

  it('offers the red 삭제 first and puts the focus on 취소', () => {
    expect(out).toMatch(
      /<button type="button" class="btn danger">삭제<\/button><button type="button" class="btn sec" data-autofocus="true">취소<\/button>/,
    )
  })
})
