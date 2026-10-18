import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { dwell } from '@/lib/dwell'

describe('dwell', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('asks for nothing while the reader flips through five items', () => {
    const asked: string[] = []
    const rest = dwell(1500, (key) => asked.push(key))
    for (const key of ['a', 'b', 'c', 'd', 'e']) {
      rest.at(key)
      vi.advanceTimersByTime(400)
    }
    rest.stop()
    vi.advanceTimersByTime(5000)
    expect(asked).toEqual([])
  })

  it('asks for exactly the one item the reader rests on', () => {
    const asked: string[] = []
    const rest = dwell(1500, (key) => asked.push(key))
    rest.at('a')
    vi.advanceTimersByTime(400)
    rest.at('b')
    vi.advanceTimersByTime(1499)
    expect(asked).toEqual([])
    rest.at('b')
    vi.advanceTimersByTime(1)
    vi.advanceTimersByTime(10_000)
    expect(asked).toEqual(['b'])
  })

  it('cancels the wait when the reader leaves for the cover', () => {
    const asked: string[] = []
    const rest = dwell(1500, (key) => asked.push(key))
    rest.at('a')
    vi.advanceTimersByTime(1000)
    rest.at(null)
    vi.advanceTimersByTime(5000)
    expect(asked).toEqual([])
  })
})
