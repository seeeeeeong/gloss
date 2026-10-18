'use client'

import { useRouter } from 'next/navigation'
import { useCallback, useEffect, useRef, useState } from 'react'
import { send } from '@/lib/api'
import { BOOKMARKS_PATH, type Bookmark, bookmarkPath } from '@/lib/bookmarks'

// Long enough to read a short line, short enough not to sit over the book.
const SAID_FOR_MS = 2400

/**
 * Bookmarks as the reader keeps and drops them, in a book or on 책갈피, and the short line each change
 * says; the server's list, and the sidebar's count, follow on a refresh.
 */
export function useBookmarks(initial: Bookmark[]) {
  const router = useRouter()
  const [marks, setMarks] = useState(initial)
  const [said, setSaid] = useState<string | null>(null)
  // A second press while a delete is on its way is not sent: it would find the bookmark gone.
  const [removing, setRemoving] = useState<ReadonlySet<string>>(new Set())
  const inFlight = useRef(new Set<string>())

  useEffect(() => {
    if (!said) return
    const timer = setTimeout(() => setSaid(null), SAID_FOR_MS)
    return () => clearTimeout(timer)
  }, [said])

  const save = useCallback(
    async (bookId: string, itemKey: string, quote: string | null) => {
      const result = await send<Bookmark>('POST', BOOKMARKS_PATH, { bookId, itemKey, quote })
      if (!result.ok) {
        setSaid(result.message)
        return
      }
      const kept = result.data
      setMarks((before) => [kept, ...before.filter((mark) => mark.id !== kept.id)])
      setSaid('책갈피에 저장했어요.')
      router.refresh()
    },
    [router],
  )

  const remove = useCallback(
    async (mark: Bookmark) => {
      if (inFlight.current.has(mark.id)) return
      inFlight.current.add(mark.id)
      setRemoving(new Set(inFlight.current))
      const result = await send<void>('DELETE', bookmarkPath(mark.id))
      inFlight.current.delete(mark.id)
      setRemoving(new Set(inFlight.current))
      if (!result.ok) {
        setSaid(result.message)
        return
      }
      setMarks((before) => before.filter((other) => other.id !== mark.id))
      setSaid('책갈피를 지웠어요.')
      router.refresh()
    },
    [router],
  )

  return { marks, said, removing, save, remove }
}
