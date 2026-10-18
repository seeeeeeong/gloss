'use client'

import './reader.css'
import './post.css'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { BookmarkButton } from '@/features/reader/components/BookmarkButton'
import { ReaderContents } from '@/features/reader/components/ReaderContents'
import { ReaderPost } from '@/features/reader/components/ReaderPost'
import { ReadingControls } from '@/features/reader/components/ReadingView'
import { Tutor } from '@/features/reader/components/Tutor'
import { useBookPosts } from '@/features/reader/hooks/useBookPosts'
import { lastReadIn, useReadingPlace } from '@/features/reader/hooks/useReadingPlace'
import { useReadingSize } from '@/features/reader/hooks/useReadingSize'
import { useStageSize } from '@/features/reader/hooks/useStageSize'
import { useTutor } from '@/features/reader/hooks/useTutor'
import { dwell, REST_MS } from '@/features/reader/utils/dwell'
import { talkedItems } from '@/features/reader/utils/tutor'
import { useBookmarks } from '@/hooks/useBookmarks'
import type { ReaderProps } from './Reader'

/**
 * A book read as posts: one item a screen, scrolled down, with the items either side at its foot.
 * The address, the last item read, the bookmarks and the tutor are the book view's.
 */
export function PostReader({ book, items, posts, start, bookmarks }: ReaderProps) {
  const index = useMemo(() => new Map(items.map((item, i) => [item.itemKey, i])), [items])
  const { viewOf, texts, versions, arrive, prefetch, retry } = useBookPosts(book.id, posts)
  const scroller = useRef<HTMLDivElement>(null)
  const text = useRef<HTMLDivElement>(null)
  const [size] = useReadingSize()
  const marks = useBookmarks(bookmarks)
  const marked = useMemo(() => new Set(marks.marks.map((mark) => mark.itemKey)), [marks.marks])
  const tutor = useTutor(book.id)
  const talked = useMemo(() => talkedItems(tutor.messages), [tutor.messages])
  const stage = useStageSize(scroller)
  const phone = stage?.phone ?? false

  const [current, setCurrent] = useState<string | null>(start)
  // With no item in the address, the one last read, else the first: there is no cover to open on.
  useEffect(() => {
    if (current === null) setCurrent(lastReadIn(book.id, index) ?? items[0]?.itemKey ?? null)
  }, [current, book.id, index, items])
  const at = current === null ? undefined : index.get(current)
  useReadingPlace({ bookId: book.id, items, index, start, current: current ?? undefined })

  // Resting on an item not yet written asks for it; its post and those either side are read.
  const resting = useRef<ReturnType<typeof dwell> | null>(null)
  const latestArrive = useRef(arrive)
  useEffect(() => {
    latestArrive.current = arrive
  })
  useEffect(() => {
    const rest = dwell(REST_MS, (key) => latestArrive.current(key))
    resting.current = rest
    return () => rest.stop()
  }, [])
  useEffect(() => {
    if (at === undefined) return
    resting.current?.at(items[at]?.itemKey ?? null)
    prefetch([at - 1, at, at + 1].flatMap((i) => items[i]?.itemKey ?? []))
  }, [at, items, prefetch])

  // After a link at the foot or in the path, the new item's heading takes focus.
  const heading = useRef(false)
  useEffect(() => {
    if (!heading.current) return
    heading.current = false
    text.current?.querySelector<HTMLElement>('h1')?.focus({ preventScroll: true })
  })

  const open = useCallback(
    (key: string) => {
      if (!index.has(key)) return
      setCurrent(key)
      scroller.current?.scrollTo({ top: 0 })
    },
    [index],
  )
  // Going to an item by name is reading it: no wait.
  const goItem = useCallback(
    (key: string) => {
      open(key)
      arrive(key)
    },
    [open, arrive],
  )
  function follow(key: string, byName: boolean) {
    heading.current = true
    if (byName) goItem(key)
    else open(key)
  }

  return (
    <div className="reader post">
      <ReaderContents
        book={book}
        items={items}
        current={current}
        phone={phone}
        viewOf={viewOf}
        marked={marked}
        talked={talked}
        onGo={goItem}
      />
      <div ref={scroller} className="post-scroll">
        {at !== undefined && (
          <ReaderPost
            book={book}
            items={items}
            at={at}
            viewOf={viewOf}
            markdown={current === null ? null : (texts.get(current) ?? null)}
            text={text}
            size={size}
            onGo={(key) => follow(key, true)}
            onTurn={(key) => follow(key, false)}
            onRetry={retry}
          />
        )}
      </div>
      <ReadingControls />
      <BookmarkButton
        current={current}
        marks={marks.marks}
        onSave={(key) => void marks.save(book.id, key, null)}
        onRemove={(mark) => void marks.remove(mark)}
      />
      <div className={marks.said ? 'toast shown' : 'toast'} role="status">
        {marks.said}
      </div>
      <Tutor
        tutor={tutor}
        items={items}
        current={current}
        phone={phone}
        width={stage?.width ?? 0}
        versions={versions}
        flow={text}
        onGo={goItem}
        onKeep={(quote) => {
          if (quote.itemKey) void marks.save(book.id, quote.itemKey, quote.text)
        }}
      />
    </div>
  )
}
