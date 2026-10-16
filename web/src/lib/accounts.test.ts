import { describe, expect, it } from 'vitest'
import { formatDateTime, roleLabel, statusLabel } from './accounts'

describe('labels', () => {
  it('names roles and statuses in Korean', () => {
    expect(roleLabel('operator')).toBe('운영자')
    expect(roleLabel('member')).toBe('회원')
    expect(statusLabel('active')).toBe('사용 중')
    expect(statusLabel('disabled')).toBe('정지')
  })

  it('shows an unknown value as it came, rather than hiding it', () => {
    expect(roleLabel('auditor')).toBe('auditor')
  })
})

describe('formatDateTime', () => {
  it('shows a time in Seoul, to the minute', () => {
    expect(formatDateTime('2026-10-04T15:30:59Z')).toBe('2026-10-05 00:30')
  })

  it('says never in words', () => {
    expect(formatDateTime(null)).toBe('없음')
  })
})
