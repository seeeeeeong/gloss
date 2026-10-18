'use client'

import { useEffect, useState } from 'react'
import type { TocItem } from '@/lib/books'
import { readerHref } from '@/lib/posts'
import { RECENT_KEY, readsFrom, withRead } from '@/lib/shelf'

const lastReadKey = (bookId: string) => `gloss.reader.${bookId}.lastRead`

/** The item last read in this book in this browser, if it is still in its contents. */
export function lastReadIn(bookId: string, index: ReadonlyMap<string, number>): string | null {
  try {
    const stored = localStorage.getItem(lastReadKey(bookId))
    return stored && index.has(stored) ? stored : null
  } catch {
    return null
  }
}

type PlaceArgs = {
  bookId: string
  items: TocItem[]
  index: ReadonlyMap<string, number>
  start: string | null
  /** The item open once the pages are laid out, null on the front spreads; undefined before. */
  current: string | null | undefined
}

/**
 * The item last read: the address names it, and the 차례 opens at its chapter and checks what
 * came before it, here and the next time the book opens.
 */
export function useReadingPlace({
  bookId,
  items,
  index,
  start,
  current,
}: PlaceArgs): string | null {
  const [lastRead, setLastRead] = useState<string | null>(start)

  useEffect(() => {
    if (start) return
    const stored = lastReadIn(bookId, index)
    if (stored) setLastRead(stored)
  }, [bookId, index, start])

  useEffect(() => {
    if (current === undefined) return
    history.replaceState(null, '', current ? readerHref(bookId, current) : `/books/${bookId}`)
    if (!current) return
    setLastRead(current)
    try {
      localStorage.setItem(lastReadKey(bookId), current)
      keepRecentRead(bookId, items, index.get(current))
    } catch {}
  }, [bookId, items, index, current])

  return lastRead
}

/** Puts the item at [at] in front of the reads the shelf offers again. */
function keepRecentRead(bookId: string, items: TocItem[], at: number | undefined) {
  const item = at === undefined ? undefined : items[at]
  if (at === undefined || !item) return
  const read = {
    bookId,
    itemKey: item.itemKey,
    title: item.title,
    position: at + 1,
    total: items.length,
  }
  const kept = readsFrom(localStorage.getItem(RECENT_KEY))
  localStorage.setItem(RECENT_KEY, JSON.stringify(withRead(kept, read)))
}
