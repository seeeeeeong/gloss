import type { ReactNode } from 'react'
import { AppShell } from '@/app/(app)/_components/AppShell'
import { bookmarksBeside, shelfOf } from '@/features/books/api/server-books'
import { requireReader } from '@/lib/server-session'

export default async function AppLayout({ children }: { children: ReactNode }) {
  const me = await requireReader('/')
  const [books, bookmarks] = await Promise.all([shelfOf(), bookmarksBeside()])
  const shelf = books.map(({ id, title }) => ({ id, title }))
  return (
    <AppShell me={me} books={shelf} marks={bookmarks.length}>
      {children}
    </AppShell>
  )
}
