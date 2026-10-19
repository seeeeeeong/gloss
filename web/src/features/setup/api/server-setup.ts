import { unstable_rethrow } from 'next/navigation'
import { chatHref, type SetupSessionSummary, sessionsPath } from '@/features/setup/api/setup'
import { serverGet } from '@/lib/server-session'

/**
 * The address of the conversation a book was chosen in: none for a book made another way, or when
 * the server cannot say, so the book still opens. A lost session still goes to sign-in.
 */
export async function chatOfBook(bookId: string): Promise<string | null> {
  try {
    const [latest] = await serverGet<SetupSessionSummary[]>(sessionsPath(bookId))
    return latest ? chatHref(latest.id) : null
  } catch (error) {
    unstable_rethrow(error)
    return null
  }
}
