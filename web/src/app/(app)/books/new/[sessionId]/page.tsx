import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import { chatKey, isSessionId, type SetupSession, sessionPath } from '@/features/setup/api/setup'
import { SetupChat } from '@/features/setup/views/SetupChat'
import { serverFind } from '@/lib/server-session'

export const metadata: Metadata = { title: '새 책' }

/** One of the reader's conversations, reopened: it goes on until a book is made from it, then is only read. */
export default async function SetupSessionPage({ params }: PageProps<'/books/new/[sessionId]'>) {
  const { sessionId } = await params
  const session = isSessionId(sessionId)
    ? await serverFind<SetupSession>(sessionPath(sessionId))
    : null
  if (!session) notFound()
  // Keyed by the session, so moving between conversations never keeps another's state.
  return <SetupChat key={chatKey(session)} initial={session} />
}
