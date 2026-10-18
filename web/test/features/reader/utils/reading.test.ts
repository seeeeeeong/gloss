import { describe, expect, it } from 'vitest'
import { DEFAULT_SIZE, lineOf, sizeFrom, stepped, viewFrom } from '@/features/reader/utils/reading'

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
  })
})

describe('stepped', () => {
  it('moves one size and stops at either end', () => {
    expect(stepped(18, 1)).toBe(20)
    expect(stepped(20, 1)).toBe(20)
    expect(stepped(16, -1)).toBe(16)
  })
})

describe('viewFrom', () => {
  it('keeps the view the reader chose, on any screen', () => {
    expect(viewFrom('book', true)).toBe('book')
    expect(viewFrom('post', false)).toBe('post')
  })

  it('reads the book on a wide screen and posts on a phone until the reader chooses', () => {
    expect(viewFrom(null, false)).toBe('book')
    expect(viewFrom(null, true)).toBe('post')
  })

  it('takes anything else kept as no choice', () => {
    expect(viewFrom('spread', false)).toBe('book')
    expect(viewFrom('', true)).toBe('post')
  })
})
