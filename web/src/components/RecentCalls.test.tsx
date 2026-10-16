import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { UsageCall } from '@/lib/usage'
import { RecentCalls } from './RecentCalls'

const now = new Date('2026-10-15T03:00:00Z')
const call: UsageCall = {
  endedAt: '2026-10-15T01:52:00Z',
  purpose: 'post',
  costMicros: 3_100,
  cacheHitTokens: 3_960,
  cacheMissTokens: 852,
  completionTokens: 2_140,
}

describe('RecentCalls', () => {
  it('lists calls under their day, each with its kind, time, cost and tokens on hover', () => {
    const out = renderToStaticMarkup(<RecentCalls calls={[call]} now={now} />)

    expect(out).toContain('최근 호출')
    expect(out).toContain('>오늘</h4>')
    expect(out).toContain('>글</span>')
    expect(out).toContain('>10:52</span>')
    expect(out).toContain('$0.003')
    expect(out).toContain('data-tip="입력 4,812 (캐시 3,960) · 출력 2,140"')
    expect(out).toContain('tabindex="0"')
    expect(out).toContain(
      '<span class="c-t" aria-hidden="true">입력 4,812 (캐시 3,960) · 출력 2,140</span>',
    )
  })

  it('gives an expired call no tooltip, so nothing to focus', () => {
    const expired = { ...call, cacheHitTokens: null, cacheMissTokens: null, completionTokens: null }
    const out = renderToStaticMarkup(<RecentCalls calls={[expired]} now={now} />)

    expect(out).not.toContain('data-tip')
    expect(out).not.toContain('tabindex')
    expect(out).not.toContain('c-t')
  })

  it('shows nothing, not even its heading, before the month has a call', () => {
    expect(renderToStaticMarkup(<RecentCalls calls={[]} now={now} />)).toBe('')
  })
})
