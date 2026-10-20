'use client'

import { useEffect, useRef } from 'react'
import { dwell, REST_MS } from '@/features/reader/utils/dwell'
import { send } from '@/lib/api'
import { readerHref } from '@/lib/posts'
import { announceRecent, readPath } from '@/lib/reads'

type PlaceArgs = {
  bookId: string
  /** The item open once it is laid out; undefined before. */
  current: string | null | undefined
}

/**
 * The item open is the one the address names, and, once the reader has stayed on it, the one the
 * book opens at the next time, on any of their devices. Turning through a book keeps nothing.
 */
export function useReadingPlace({ bookId, current }: PlaceArgs) {
  const keeping = useRef<ReturnType<typeof dwell> | null>(null)
  useEffect(() => {
    const keep = dwell(REST_MS, (itemKey) => void keepRead(bookId, itemKey))
    keeping.current = keep
    return () => keep.stop()
  }, [bookId])

  useEffect(() => {
    if (!current) return
    history.replaceState(null, '', readerHref(bookId, current))
    keeping.current?.at(current)
  }, [bookId, current])
}

async function keepRead(bookId: string, itemKey: string) {
  const result = await send<void>('PUT', readPath(bookId), { itemKey })
  if (result.ok) announceRecent()
}
