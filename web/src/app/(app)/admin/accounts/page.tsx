import type { Metadata } from 'next'
import { AdminAccounts } from '@/components/AdminAccounts'
import { Page } from '@/components/Page'
import type { AdminAccount } from '@/lib/accounts'
import { requireReader, serverGet } from '@/lib/server-session'

export const metadata: Metadata = { title: '계정' }

export default async function AdminAccountsPage() {
  const me = await requireReader('/admin/accounts')
  const accounts = await serverGet<AdminAccount[]>('/api/admin/accounts')
  return (
    <Page>
      <AdminAccounts accounts={accounts} myId={me.id} />
    </Page>
  )
}
