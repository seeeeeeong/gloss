'use client'

import { useRouter } from 'next/navigation'
import { useCallback, useEffect, useState } from 'react'
import { type ApiResult, load, send } from '@/lib/api'
import {
  type OfferedBook,
  SESSION_PARAM,
  SETUP_PATH,
  type SetupSession,
  sessionPath,
  startMayHaveLanded,
  type TypedContents,
} from '@/lib/setup'

// The model answers in a few seconds to a minute; looking every two is quick enough and light.
const LOOK_AGAIN_MS = 2000

/** What the reader just did, shown until the server answers. */
export type Pending = { kind: 'say' | 'pick'; text: string }

/**
 * A setup session as the page holds it, and the reader's steps on it. The first message starts the
 * session and puts it in the address; a pick calls [onChosen], so its contents open.
 */
export function useSetupSession(initial: SetupSession | null, onChosen: () => void) {
  const router = useRouter()
  const [session, setSession] = useState(initial)
  const [pending, setPending] = useState<Pending | null>(null)
  const [error, setError] = useState<string | null>(null)

  // A message is answered after the request that sent it returns, here or in another tab: look
  // again until its answer is kept.
  const waiting = session?.searching === true && pending === null
  const id = session?.id
  useEffect(() => {
    if (!waiting || !id) return
    const timer = setInterval(async () => {
      const result = await load<SetupSession>(sessionPath(id))
      if (result.ok) setSession(result.data)
    }, LOOK_AGAIN_MS)
    return () => clearInterval(timer)
  }, [waiting, id])

  // Refused because the session moved on, in another tab or a lookup's wait: show it as it is now.
  async function catchUp(result: ApiResult<SetupSession>) {
    if (result.ok || result.status !== 409 || !session) return
    const now = await load<SetupSession>(sessionPath(session.id))
    if (now.ok) setSession(now.data)
  }

  async function say(text: string) {
    setPending({ kind: 'say', text })
    setError(null)
    const path = session ? sessionPath(session.id, 'messages') : SETUP_PATH
    const result = await send<SetupSession>('POST', path, { text, revision: session?.revision })
    setPending(null)
    await catchUp(result)
    if (!result.ok) return setError(result.message)
    if (!session) window.history.replaceState(null, '', `?${SESSION_PARAM}=${result.data.id}`)
    setSession(result.data)
  }

  async function pick(book: OfferedBook) {
    if (!session) return
    setPending({ kind: 'pick', text: book.title })
    setError(null)
    const result = await send<SetupSession>('POST', sessionPath(session.id, 'pick'), {
      ref: book.ref,
      revision: session.revision,
    })
    setPending(null)
    await catchUp(result)
    if (!result.ok) return setError(result.message)
    setSession(result.data)
    onChosen()
  }

  /** Contents the reader typed in, held as a pick's are; a book of their own may start the session. */
  async function type(typed: TypedContents) {
    const path = session
      ? sessionPath(session.id, 'typed-contents')
      : `${SETUP_PATH}/typed-contents`
    const result = await send<SetupSession>('POST', path, { ...typed, revision: session?.revision })
    await catchUp(result)
    if (!result.ok) return result
    if (!session) window.history.replaceState(null, '', `?${SESSION_PARAM}=${result.data.id}`)
    setError(null)
    setSession(result.data)
    onChosen()
    return result
  }

  /** The held contents, as the reader edited them; the book is still not made. */
  async function edit(toc: string) {
    if (!session) return null
    const path = sessionPath(session.id, 'contents')
    const result = await send<SetupSession>('PUT', path, { toc, revision: session.revision })
    if (result.ok) setSession(result.data)
    else await catchUp(result)
    return result
  }

  /** Makes the book from the held contents, as the reader saw them at the session's revision. */
  async function start() {
    if (!session) return null
    const sent = await send<SetupSession>('POST', sessionPath(session.id, 'confirm'), {
      revision: session.revision,
    })
    const result = startMayHaveLanded(sent)
      ? await load<SetupSession>(sessionPath(session.id))
      : sent
    const started = result.ok && result.data.state === 'confirmed' && result.data.bookId
    if (!started) {
      await catchUp(sent)
      return sent
    }
    setSession(result.data)
    // Stays busy: the book's page is on its way; the sidebar lists the book too.
    router.push(`/books/${result.data.bookId}`)
    router.refresh()
    return result
  }

  const reset = useCallback(() => {
    setSession(null)
    setPending(null)
    setError(null)
  }, [])

  return { session, pending, error, say, pick, type, edit, start, reset }
}
