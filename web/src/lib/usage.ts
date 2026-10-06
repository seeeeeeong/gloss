/** What GET /api/me/usage returns, in µUSD; estimated from documented prices, not DeepSeek's bill. */
export type Usage = {
  billingMonth: string
  limitMicros: number | null
  spentMicros: number
  inFlightMicros: number
  remainingMicros: number | null
}
