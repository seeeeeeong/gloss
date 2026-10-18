import { dollarsToMicros } from '@/features/me/utils/money'

/** What GET /api/me/usage returns, in µUSD; estimated from documented prices, not DeepSeek's bill. */
export type Usage = {
  billingMonth: string
  limitMicros: number | null
  spentMicros: number
  inFlightMicros: number
  remainingMicros: number | null
}

/** One of GET /api/me/usage/calls: a call this month that ended at a cost, newest first. */
export type UsageCall = {
  endedAt: string
  purpose: string
  costMicros: number
  cacheHitTokens: number | null
  cacheMissTokens: number | null
  completionTokens: number | null
}

const MICROS = 1_000_000
const CENT = 10_000

// As the server's spending_limit check: a larger number is far more likely a typo.
const MAX_LIMIT_MICROS = 1_000 * MICROS

export const LIMIT_ERROR = '0에서 1,000 사이 금액으로 정해 주세요.'

// Dollars with an optional sign, commas only between groups of three, and up to six decimals.
const AMOUNT = /^\$?\s*(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,6})?$/

/** "$5.00" or "1,000" as µUSD, or null when it is no amount the server would keep. */
export function parseLimit(text: string): number | null {
  const trimmed = text.trim()
  if (!AMOUNT.test(trimmed)) return null
  const micros = dollarsToMicros(trimmed.replace(/[$,\s]/g, ''))
  return micros !== null && micros <= MAX_LIMIT_MICROS ? micros : null
}

/** The limit as the field shows it to edit: to the cent, unless it was set finer. */
export function limitText(micros: number): string {
  return micros % CENT === 0 ? (micros / MICROS).toFixed(2) : String(micros / MICROS)
}

const KINDS: Record<string, string> = {
  chat: '튜터',
  plan: '목차 준비',
  post: '글',
  check: '글 확인',
}

export const callKind = (purpose: string) => KINDS[purpose] ?? purpose

// Half a tenth of a cent: below it three places would round a charged call to $0.000.
const SMALLEST_SHOWN = 500

/** A call's cost to a tenth of a cent: each is small, and the month's total is above them. */
export const callCost = (micros: number) =>
  micros > 0 && micros < SMALLEST_SHOWN ? '<$0.001' : `$${(micros / MICROS).toFixed(3)}`

const COUNT = new Intl.NumberFormat('en-US')

/** "입력 4,812 (캐시 3,960) · 출력 2,140"; null for a call that expired before it said. */
export function callTokens(call: UsageCall): string | null {
  const { cacheHitTokens: hit, cacheMissTokens: miss, completionTokens: out } = call
  if (hit === null || miss === null || out === null) return null
  const cached = hit > 0 ? ` (캐시 ${COUNT.format(hit)})` : ''
  return `입력 ${COUNT.format(hit + miss)}${cached} · 출력 ${COUNT.format(out)}`
}

// Readers are in Korea; the server sends UTC.
const SEOUL_DAY = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul', dateStyle: 'short' })
const SEOUL_TIME = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
})

export const callTime = (iso: string) => SEOUL_TIME.format(new Date(iso))

export type CallDay = { key: string; label: string; calls: UsageCall[] }

/** Calls under 오늘, 어제, then "10월 12일", by the day in Seoul, in the order they came. */
export function callDays(calls: UsageCall[], now: Date): CallDay[] {
  const today = SEOUL_DAY.format(now)
  const dayNumber = (key: string) => Date.parse(`${key}T00:00:00Z`) / 86_400_000
  const label = (key: string) => {
    const ago = dayNumber(today) - dayNumber(key)
    if (ago === 0) return '오늘'
    if (ago === 1) return '어제'
    const [, month, day] = key.split('-')
    return `${Number(month)}월 ${Number(day)}일`
  }
  const days: CallDay[] = []
  for (const call of calls) {
    const key = SEOUL_DAY.format(new Date(call.endedAt))
    const last = days.at(-1)
    if (last?.key === key) last.calls.push(call)
    else days.push({ key, label: label(key), calls: [call] })
  }
  return days
}
