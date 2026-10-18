// The server counts money in µUSD, whole numbers: $1 is 1,000,000.
const MICROS = 1_000_000
const CENT = 10_000
const TEN_THOUSANDTH = 100

/**
 * Dollars to the cent; under a cent to four places, and under that to the micro-dollar, so a
 * call never reads as free.
 */
export function formatUsd(micros: number): string {
  const sign = micros < 0 ? '-' : ''
  const amount = Math.abs(micros)
  const places = amount === 0 || amount >= CENT ? 2 : amount >= TEN_THOUSANDTH ? 4 : 6
  return `${sign}$${(amount / MICROS).toFixed(places)}`
}

/** "19.99" as 19,990,000, read digit by digit so no floating point creeps in. Null if not a positive amount. */
export function dollarsToMicros(text: string): number | null {
  const match = /^(\d+)(?:\.(\d{1,6}))?$/.exec(text.trim())
  if (!match) return null
  const micros = Number(match[1]) * MICROS + Number((match[2] ?? '').padEnd(6, '0'))
  return micros > 0 ? micros : null
}
