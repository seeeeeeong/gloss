import type { Metadata } from 'next'
import { redirect } from 'next/navigation'
import { chatHref, isSessionId, SESSION_PARAM } from '@/features/setup/api/setup'
import { SetupChat } from '@/features/setup/views/SetupChat'

export const metadata: Metadata = { title: '새 책' }

/** A fresh conversation; an address from before conversations had their own goes on to its own. */
export default async function NewBookPage({ searchParams }: PageProps<'/books/new'>) {
  const id = (await searchParams)[SESSION_PARAM]
  if (typeof id === 'string' && isSessionId(id)) redirect(chatHref(id))
  return <SetupChat initial={null} />
}
