import type { ReactNode } from 'react'
import { AppShell } from '@/components/AppShell'
import { shelfOf } from '@/lib/server-books'
import { requireReader } from '@/lib/server-session'

export default async function AppLayout({ children }: { children: ReactNode }) {
  const me = await requireReader('/')
  const books = await shelfOf()
  const shelf = books.map(({ id, title }) => ({ id, title }))
  return (
    <AppShell me={me} books={shelf}>
      {children}
    </AppShell>
  )
}
