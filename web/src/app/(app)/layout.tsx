import type { ReactNode } from 'react'
import { AppShell } from '@/app/(app)/_components/AppShell'
import { bookmarksBeside, recentBeside, shelfOf } from '@/features/books/api/server-books'
import { requireReader } from '@/lib/server-session'

export default async function AppLayout({ children }: { children: ReactNode }) {
  const me = await requireReader('/')
  const [books, bookmarks, recent] = await Promise.all([
    shelfOf(),
    bookmarksBeside(),
    recentBeside(),
  ])
  const shelf = books.map(({ id, title }) => ({ id, title }))
  return (
    <AppShell me={me} books={shelf} recent={recent} marks={bookmarks.length}>
      {children}
    </AppShell>
  )
}
