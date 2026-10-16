'use client'

import { type RefObject, useEffect } from 'react'

/**
 * While `open`, Escape or a press outside `root` calls `close`; Escape also hands focus back to
 * `trigger`, which a press elsewhere has already taken.
 */
export function useDismiss(
  open: boolean,
  root: RefObject<HTMLElement | null>,
  trigger: RefObject<HTMLElement | null>,
  close: () => void,
) {
  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      close()
      trigger.current?.focus()
    }
    const onPress = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) close()
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onPress)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onPress)
    }
  }, [open, root, trigger, close])
}
