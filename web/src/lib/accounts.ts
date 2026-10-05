export type AdminAccount = {
  id: string
  loginId: string
  displayName: string
  role: string
  status: string
  mustChangePassword: boolean
  createdAt: string
  lastLoginAt: string | null
}

export type CreatedAccount = { account: AdminAccount; temporaryPassword: string }

const ROLES: Record<string, string> = { operator: '운영자', member: '회원' }
const STATUSES: Record<string, string> = { active: '사용 중', disabled: '정지' }

export const roleLabel = (role: string) => ROLES[role] ?? role
export const statusLabel = (status: string) => STATUSES[status] ?? status

// Readers and the operator are in Korea; the server sends UTC.
const SEOUL = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(iso: string | null): string {
  return iso ? SEOUL.format(new Date(iso)) : '—'
}
