'use client'

import { useEffect } from 'react'
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
  /** The item open once it is laid out; undefined before. */
  current: string | null | undefined
}

/** The item open is the one the address names, and the one the book opens at the next time. */
export function useReadingPlace({ bookId, items, index, current }: PlaceArgs) {
  useEffect(() => {
    if (!current) return
    history.replaceState(null, '', readerHref(bookId, current))
    try {
      localStorage.setItem(lastReadKey(bookId), current)
      keepRecentRead(bookId, items, index.get(current))
    } catch {}
  }, [bookId, items, index, current])
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
