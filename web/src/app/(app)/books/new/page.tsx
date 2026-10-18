import type { Metadata } from 'next'
import {
  chatKey,
  isSessionId,
  SESSION_PARAM,
  type SetupSession,
  sessionPath,
} from '@/features/setup/api/setup'
import { SetupChat } from '@/features/setup/views/SetupChat'
import { serverFind } from '@/lib/server-session'

export const metadata: Metadata = { title: '새 책' }

export default async function NewBookPage({ searchParams }: PageProps<'/books/new'>) {
  const id = (await searchParams)[SESSION_PARAM]
  const session =
    typeof id === 'string' && isSessionId(id)
      ? await serverFind<SetupSession>(sessionPath(id))
      : null
  // Keyed by the session, so moving between conversations in the address never keeps another's state.
  return <SetupChat key={chatKey(session)} initial={session} />
}
