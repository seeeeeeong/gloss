import type { ReactNode } from 'react'
import { AppShell } from '@/components/AppShell'
import { requireReader } from '@/lib/server-session'

export default async function AppLayout({ children }: { children: ReactNode }) {
  const me = await requireReader('/')
  return <AppShell me={me}>{children}</AppShell>
}
