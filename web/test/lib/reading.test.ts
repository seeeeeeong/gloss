import { describe, expect, it } from 'vitest'
import { DEFAULT_SIZE, lineOf, sizeFrom, sizeLabel, stepped } from '@/lib/reading'

describe('sizeFrom', () => {
  it('reads one of the three sizes and takes anything else as the default', () => {
    expect(sizeFrom('20')).toBe(20)
    expect(sizeFrom('17')).toBe(DEFAULT_SIZE)
    expect(sizeFrom(null)).toBe(DEFAULT_SIZE)
    expect(DEFAULT_SIZE).toBe(18)
  })
})

describe('lineOf', () => {
  it('gives each size a line of whole pixels near 1.8 times it', () => {
    expect([lineOf(16), lineOf(18), lineOf(20)]).toEqual([28, 32, 36])
    expect(sizeLabel(16)).toBe('작게')
  })
})

describe('stepped', () => {
  it('moves one size and stops at either end', () => {
    expect(stepped(18, 1)).toBe(20)
    expect(stepped(20, 1)).toBe(20)
    expect(stepped(16, -1)).toBe(16)
  })
})
