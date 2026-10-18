import { describe, expect, it } from 'vitest'
import { keyStatusLabel } from '@/lib/apiKey'

describe('keyStatusLabel', () => {
  it('says how a key stands in Korean', () => {
    expect(keyStatusLabel('valid')).toBe('정상')
    expect(keyStatusLabel('invalid')).toBe('DeepSeek이 거절함')
    expect(keyStatusLabel('unreadable')).toBe('다시 입력 필요')
  })

  it('shows an unknown status as it came, rather than hiding it', () => {
    expect(keyStatusLabel('paused')).toBe('paused')
  })
})
