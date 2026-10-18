import { describe, expect, it } from 'vitest'
import { formatDateTime } from '@/utils/format'

describe('formatDateTime', () => {
  it('shows a time in Seoul, to the minute', () => {
    expect(formatDateTime('2026-10-04T15:30:59Z')).toBe('2026-10-05 00:30')
  })

  it('says never in words', () => {
    expect(formatDateTime(null)).toBe('없음')
  })
})
