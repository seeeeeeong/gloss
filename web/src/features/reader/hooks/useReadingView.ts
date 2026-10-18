'use client'

import { useCallback, useSyncExternalStore } from 'react'
import { PHONE, type ReadingView, viewFrom } from '@/features/reader/utils/reading'

const VIEW_KEY = 'gloss.reader.view'

// The page's own controls tell each other at once; another tab hears it through the storage event.
const listeners = new Set<() => void>()
// The view chosen on this page while storage refuses it, blocked or full; storage itself otherwise.
let fallback: ReadingView | null = null

function subscribe(onChange: () => void) {
  const onStorage = (event: StorageEvent) => {
    if (event.key !== VIEW_KEY) return
    fallback = null
    onChange()
  }
  // Until the reader chooses, the view follows the screen across the phone's width.
  const media = matchMedia(PHONE)
  listeners.add(onChange)
  addEventListener('storage', onStorage)
  media.addEventListener('change', onChange)
  return () => {
    listeners.delete(onChange)
    removeEventListener('storage', onStorage)
    media.removeEventListener('change', onChange)
  }
}

function current(): ReadingView {
  if (fallback !== null) return fallback
  const phone = matchMedia(PHONE).matches
  try {
    return viewFrom(localStorage.getItem(VIEW_KEY), phone)
  } catch {
    return viewFrom(null, phone)
  }
}

/**
 * The book's view, kept in this browser; null on the server, which cannot tell a phone, so neither
 * view is drawn and then thrown away.
 */
export function useReadingView(): [ReadingView | null, (view: ReadingView) => void] {
  const view = useSyncExternalStore(subscribe, current, () => null)
  const set = useCallback((next: ReadingView) => {
    try {
      localStorage.setItem(VIEW_KEY, next)
      fallback = null
    } catch {
      fallback = next
    }
    for (const listener of listeners) listener()
  }, [])
  return [view, set]
}
