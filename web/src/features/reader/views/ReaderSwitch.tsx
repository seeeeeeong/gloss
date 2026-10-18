'use client'

import { useState } from 'react'
import { useReadingView } from '@/features/reader/hooks/useReadingView'
import { itemOfSearch } from '@/features/reader/utils/reader'
import type { ReadingView } from '@/features/reader/utils/reading'
import { PostReader } from './PostReader'
import { Reader, type ReaderProps } from './Reader'

/**
 * The book in the view the reader chose. A switch opens the other view on the item the address
 * names, which either view keeps current; the page's own start is only the first.
 */
export function ReaderSwitch(props: ReaderProps) {
  const [view] = useReadingView()
  const [shown, setShown] = useState<{ view: ReadingView | null; start: string | null }>({
    view: null,
    start: props.start,
  })
  if (view !== shown.view) {
    const start = shown.view === null ? props.start : itemOfSearch(location.search, props.items)
    setShown({ view, start })
  }
  if (shown.view === null) return null
  const View = shown.view === 'post' ? PostReader : Reader
  return <View {...props} start={shown.start} />
}
