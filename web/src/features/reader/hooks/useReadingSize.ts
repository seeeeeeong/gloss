'use client'

import { useCallback, useSyncExternalStore } from 'react'
import { DEFAULT_SIZE, type ReadingSize, sizeFrom } from '@/features/reader/utils/reading'

const SIZE_KEY = 'gloss.reader.size'

// The page's own controls tell each other at once; another tab hears it through the storage event.
const listeners = new Set<() => void>()
// The size chosen on this page while storage refuses it, blocked or full; storage itself otherwise.
let fallback: ReadingSize | null = null

function subscribe(onChange: () => void) {
  const onStorage = (event: StorageEvent) => {
    if (event.key !== SIZE_KEY) return
    fallback = null
    onChange()
  }
  listeners.add(onChange)
  addEventListener('storage', onStorage)
  return () => {
    listeners.delete(onChange)
    removeEventListener('storage', onStorage)
  }
}

function current(): ReadingSize {
  if (fallback !== null) return fallback
  try {
    return sizeFrom(localStorage.getItem(SIZE_KEY))
  } catch {
    return DEFAULT_SIZE
  }
}

/** The book's text size, kept in this browser; the reader and every control read the same one. */
export function useReadingSize(): [ReadingSize, (size: ReadingSize) => void] {
  const size = useSyncExternalStore(subscribe, current, () => DEFAULT_SIZE)
  const set = useCallback((next: ReadingSize) => {
    try {
      localStorage.setItem(SIZE_KEY, String(next))
      fallback = null
    } catch {
      fallback = next
    }
    for (const listener of listeners) listener()
  }, [])
  return [size, set]
}
