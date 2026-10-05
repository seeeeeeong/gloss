import type { Metadata } from 'next'
import { AdminAccounts } from '@/components/AdminAccounts'
import type { AdminAccount } from '@/lib/accounts'
import { requireReader, serverGet } from '@/lib/server-session'

export const metadata: Metadata = { title: '계정 관리' }

export default async function AdminAccountsPage() {
  const me = await requireReader('/admin/accounts')
  const accounts = await serverGet<AdminAccount[]>('/api/admin/accounts')
  return (
    <div className="page page-wide">
      <h1 className="page-title">계정 관리</h1>
      <p className="page-lead">
        계정을 만들면 임시 비밀번호가 한 번만 보입니다. 받는 사람은 첫 로그인에서 비밀번호를
        바꿉니다.
      </p>
      <AdminAccounts accounts={accounts} myId={me.id} />
    </div>
  )
}
