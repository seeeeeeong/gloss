/** What GET /api/me/api-key returns: never the key, only its last four and how it stands. */
export type ApiKey = {
  provider: string
  last4: string
  status: string
  validatedAt: string | null
  createdAt: string
}

const STATUSES: Record<string, string> = {
  valid: '정상',
  invalid: 'DeepSeek이 거절함',
  unreadable: '다시 입력 필요',
}

export const keyStatusLabel = (status: string) => STATUSES[status] ?? status
