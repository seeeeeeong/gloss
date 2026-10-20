'use client'

import { useMemo, useState } from 'react'
import { useReadingView } from '@/features/reader/hooks/useReadingView'
import { itemOfSearch, readableItems } from '@/features/reader/utils/reader'
import type { ReadingView } from '@/features/reader/utils/reading'
import { PostReader } from './PostReader'
import { Reader, type ReaderProps } from './Reader'

/**
 * The book in the view the reader chose, without its front matter. Either view opens on the item
 * the page starts at, the one the address names or else the one last read, else the first; a
 * switch reads the address, which either view keeps current, since the page's own start is only the first.
 */
export function ReaderSwitch(props: ReaderProps) {
  const { start } = props
  const [view] = useReadingView()
  const items = useMemo(() => readableItems(props.items), [props.items])
  const keys = useMemo(() => new Set(items.map((item) => item.itemKey)), [items])
  const [shown, setShown] = useState<{ view: ReadingView | null; start: string | null }>({
    view: null,
    start: null,
  })
  if (view !== shown.view) {
    const named = shown.view === null ? start : itemOfSearch(location.search, items)
    // An address naming front matter, or an item no longer there, opens the first item.
    const at = named !== null && keys.has(named) ? named : null
    setShown({ view, start: at ?? items[0]?.itemKey ?? null })
  }
  if (shown.view === null) return null
  const View = shown.view === 'post' ? PostReader : Reader
  return <View {...props} items={items} start={shown.start} />
}
