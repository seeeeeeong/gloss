import { describe, expect, it } from 'vitest'
import {
  callCost,
  callDays,
  callKind,
  callTime,
  callTokens,
  limitText,
  parseLimit,
  type UsageCall,
} from '@/lib/usage'

const call = (endedAt: string, overrides: Partial<UsageCall> = {}): UsageCall => ({
  endedAt,
  purpose: 'post',
  costMicros: 3_100,
  cacheHitTokens: 3_960,
  cacheMissTokens: 852,
  completionTokens: 2_140,
  ...overrides,
})

describe('parseLimit', () => {
  it('reads dollars as typed, a sign and commas too', () => {
    expect(parseLimit('5')).toBe(5_000_000)
    expect(parseLimit('$1,000.00')).toBe(1_000_000_000)
    expect(parseLimit(' 0.5 ')).toBe(500_000)
  })

  it('refuses nothing, too much and what is not a number', () => {
    expect(parseLimit('0')).toBeNull()
    expect(parseLimit('1000.01')).toBeNull()
    expect(parseLimit('five')).toBeNull()
  })

  it('refuses separators where they do not group thousands', () => {
    expect(parseLimit('1,00')).toBeNull()
    expect(parseLimit('1 2')).toBeNull()
    expect(parseLimit('$$5')).toBeNull()
    expect(parseLimit('10,00.5')).toBeNull()
    expect(parseLimit('$ 5')).toBe(5_000_000)
  })
})

describe('limitText', () => {
  it('shows cents, or the finer amount that was set', () => {
    expect(limitText(5_000_000)).toBe('5.00')
    expect(limitText(5_555_000)).toBe('5.555')
  })
})

describe('a call', () => {
  it('reads its kind, cost to three places, and time in Seoul', () => {
    expect(callKind('post')).toBe('글')
    expect(callKind('chat')).toBe('튜터')
    expect(callKind('plan')).toBe('목차 준비')
    expect(callKind('later')).toBe('later')
    expect(callCost(3_100)).toBe('$0.003')
    expect(callCost(35_100)).toBe('$0.035')
    expect(callCost(1)).toBe('<$0.001')
    expect(callCost(499)).toBe('<$0.001')
    expect(callCost(500)).toBe('$0.001')
    expect(callTime('2026-10-15T01:52:00Z')).toBe('10:52')
  })

  it('spells out its tokens, the cached share only when there is one', () => {
    expect(callTokens(call('2026-10-15T01:52:00Z'))).toBe('입력 4,812 (캐시 3,960) · 출력 2,140')
    expect(callTokens(call('2026-10-15T01:52:00Z', { cacheHitTokens: 0 }))).toBe(
      '입력 852 · 출력 2,140',
    )
    expect(
      callTokens(
        call('2026-10-15T01:52:00Z', {
          cacheHitTokens: null,
          cacheMissTokens: null,
          completionTokens: null,
        }),
      ),
    ).toBeNull()
  })
})

describe('callDays', () => {
  it('groups by the day in Seoul: today, yesterday, then the date', () => {
    const now = new Date('2026-10-15T03:00:00Z')
    const days = callDays(
      [
        call('2026-10-15T01:52:00Z'),
        call('2026-10-14T16:10:00Z'),
        call('2026-10-14T12:08:00Z'),
        call('2026-10-12T13:15:00Z'),
      ],
      now,
    )

    expect(days.map((day) => [day.label, day.calls.length])).toEqual([
      ['오늘', 2],
      ['어제', 1],
      ['10월 12일', 1],
    ])
  })
})
