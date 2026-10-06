import { describe, expect, it } from 'vitest'
import { dollarsToMicros, formatUsd } from './money'

describe('formatUsd', () => {
  it('shows dollars to the cent', () => {
    expect(formatUsd(5_000_000)).toBe('$5.00')
    expect(formatUsd(1_234_567)).toBe('$1.23')
  })

  it('shows less than a cent to four places, so a single call does not read as free', () => {
    expect(formatUsd(518)).toBe('$0.0005')
    expect(formatUsd(0)).toBe('$0.00')
  })

  it('shows an amount too small for four places to the micro-dollar, never as nothing', () => {
    expect(formatUsd(49)).toBe('$0.000049')
    expect(formatUsd(1)).toBe('$0.000001')
  })

  it('keeps the sign of an amount past the limit', () => {
    expect(formatUsd(-250_000)).toBe('-$0.25')
  })
})

describe('dollarsToMicros', () => {
  it('reads dollars as typed', () => {
    expect(dollarsToMicros('5')).toBe(5_000_000)
    expect(dollarsToMicros(' 0.25 ')).toBe(250_000)
    expect(dollarsToMicros('1000')).toBe(1_000_000_000)
  })

  it('refuses what is not an amount, nothing, or more than six decimal places', () => {
    expect(dollarsToMicros('')).toBeNull()
    expect(dollarsToMicros('abc')).toBeNull()
    expect(dollarsToMicros('0')).toBeNull()
    expect(dollarsToMicros('-1')).toBeNull()
    expect(dollarsToMicros('0.0000001')).toBeNull()
  })

  it('counts exactly, without floating point drift', () => {
    expect(dollarsToMicros('0.29')).toBe(290_000)
    expect(dollarsToMicros('19.99')).toBe(19_990_000)
  })
})
