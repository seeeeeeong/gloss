import { describe, expect, it } from 'vitest'
import { roleLabel, statusLabel } from '@/features/admin/api/accounts'

describe('labels', () => {
  it('names roles and statuses in Korean', () => {
    expect(roleLabel('operator')).toBe('운영자')
    expect(roleLabel('member')).toBe('독자')
    expect(statusLabel('active')).toBe('사용 중')
    expect(statusLabel('disabled')).toBe('사용 중지')
  })

  it('shows an unknown value as it came, rather than hiding it', () => {
    expect(roleLabel('auditor')).toBe('auditor')
  })
})
