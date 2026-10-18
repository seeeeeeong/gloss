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

const ROLES: Record<string, string> = { operator: '운영자', member: '독자' }
const STATUSES: Record<string, string> = { active: '사용 중', disabled: '사용 중지' }

export const roleLabel = (role: string) => ROLES[role] ?? role
export const statusLabel = (status: string) => STATUSES[status] ?? status
