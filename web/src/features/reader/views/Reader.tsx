'use client'

import './reader.css'
import {
  type CSSProperties,
  type FocusEvent,
  memo,
  type RefObject,
  type TouchEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { BookmarkButton } from '@/features/reader/components/BookmarkButton'
import { Edges, PageChrome, Pager } from '@/features/reader/components/ReaderChrome'
import { ReaderContents } from '@/features/reader/components/ReaderContents'
import { ItemArticle, type ReaderBook } from '@/features/reader/components/ReaderPages'
import { ReadingControls } from '@/features/reader/components/ReadingView'
import { Tutor } from '@/features/reader/components/Tutor'
import { useBookPosts } from '@/features/reader/hooks/useBookPosts'
import { useReadingPlace } from '@/features/reader/hooks/useReadingPlace'
import { useReadingSize } from '@/features/reader/hooks/useReadingSize'
import { useSpreads } from '@/features/reader/hooks/useSpreads'
import { useStageGeometry } from '@/features/reader/hooks/useStageGeometry'
import { useTutor } from '@/features/reader/hooks/useTutor'
import { dwell, REST_MS } from '@/features/reader/utils/dwell'
import {
  type Geometry,
  type ItemView,
  itemsAround,
  openItemOf,
  readingItemOf,
  turnsPageFrom,
  visibleUnits,
} from '@/features/reader/utils/reader'
import { lineOf } from '@/features/reader/utils/reading'
import { talkedItems } from '@/features/reader/utils/tutor'
import { useBookmarks } from '@/hooks/useBookmarks'
import type { Bookmark } from '@/lib/bookmarks'
import type { TocItem } from '@/lib/books'
import type { PostSummary } from '@/lib/posts'

// A swipe shorter than this springs back.
const SWIPE = 40
const TURN_KEYS: Record<string, -1 | 1> = {
  ArrowLeft: -1,
  PageUp: -1,
  ArrowRight: 1,
  PageDown: 1,
}

export type ReaderProps = {
  book: ReaderBook
  items: TocItem[]
  posts: PostSummary[]
  start: string | null
  /** The bookmarks the reader keeps in this book. */
  bookmarks: Bookmark[]
}

/**
 * A book read page by page: every item flows through one multi-column box, a column a page,
 * turned a spread at a time. Pages are counted inside their item, since a
 * resize or a newly written item moves every page after it.
 */
export function Reader({ book, items, posts, start, bookmarks }: ReaderProps) {
  const index = useMemo(() => new Map(items.map((item, i) => [item.itemKey, i])), [items])
  const { viewOf, texts, versions, arrive, prefetch, retry } = useBookPosts(book.id, posts)
  const stage = useRef<HTMLDivElement>(null)
  const flow = useRef<HTMLDivElement>(null)
  const [size] = useReadingSize()
  const marks = useBookmarks(bookmarks)
  const marked = useMemo(() => new Set(marks.marks.map((mark) => mark.itemKey)), [marks.marks])
  const tutor = useTutor(book.id)
  const talked = useMemo(
    () => talkedItems(tutor.sessions, tutor.messages),
    [tutor.sessions, tutor.messages],
  )
  const line = lineOf(size)
  const { geo, phone, width } = useStageGeometry(stage, line)

  const [focus, setFocus] = useState<string | null>(start)
  const spreads = useSpreads(flow, geo, {
    unit: index.get(start ?? '') ?? 0,
    offset: 0,
  })
  const { layout, first, cols, settled } = spreads

  const currentKey = openItemOf({ items, layout, first, cols, focus })

  // Resting on an item not yet written asks for it; the posts on and beside the spread are read.
  const resting = useRef<ReturnType<typeof dwell> | null>(null)
  useEffect(() => {
    const rest = dwell(REST_MS, (key) => latest.current.arrive(key))
    resting.current = rest
    return () => rest.stop()
  }, [])
  useEffect(() => {
    if (!settled.current || !layout) return
    const keyAt = (i: number) => items[i]?.itemKey ?? ''
    const at = focus === null ? undefined : index.get(focus)
    const reading = readingItemOf({
      layout,
      first,
      cols,
      focus: at ?? null,
      viewOf: (i) => viewOf(keyAt(i)),
    })
    resting.current?.at(keyAt(reading))
    prefetch(itemsAround(visibleUnits(layout, first, cols), items.length).map(keyAt))
  })

  const ready = layout !== null
  useReadingPlace({ bookId: book.id, current: ready ? currentKey : undefined })

  function turn(direction: -1 | 1) {
    if (spreads.turn(direction)) setFocus(null)
  }

  function goItem(key: string) {
    const at = index.get(key)
    if (at === undefined) return
    setFocus(key)
    spreads.open(at)
    // Going to an item by name is reading it: no wait.
    arrive(key)
  }

  // Tabbing to a link on another page turns to it.
  function onFocusIn(event: FocusEvent<HTMLElement>) {
    event.currentTarget.scrollLeft = 0
    const el = flow.current
    if (!el || !geo) return
    const x = event.target.getBoundingClientRect().left - el.getBoundingClientRect().left
    if (spreads.show(Math.max(0, Math.floor((x + 2) / geo.pw)))) setFocus(null)
  }

  // What the memoised pages and the key listener call, kept current without re-rendering them.
  const latest = useRef({ turn, goItem, arrive })
  useEffect(() => {
    latest.current = { turn, goItem, arrive }
  })
  // From the tutor, a divider goes to its item and leaves focus where it was.
  const onJump = useCallback((key: string) => latest.current.goItem(key), [])
  useTurnKeys(latest)
  const swipe = useSwipe({ onDrag: spreads.follow, onTurn: turn, onSpringBack: spreads.springBack })

  return (
    <div className="reader">
      <ReaderContents
        book={book}
        items={items}
        current={currentKey}
        phone={phone}
        viewOf={viewOf}
        marked={marked}
        talked={talked}
        onGo={goItem}
      />
      <div ref={stage} className="stage">
        <section
          className="book"
          aria-label={book.title}
          aria-roledescription="책"
          style={bookStyle(geo, ready)}
          onFocus={onFocusIn}
          {...swipe}
        >
          <div
            className={spreads.slide ? 'track' : 'track still'}
            style={{ transform: `translateX(${-first * (geo?.pw ?? 0) + spreads.drag}px)` }}
          >
            <div ref={flow} className="flow" style={flowStyle(geo, size, line)}>
              <BookPages items={items} viewOf={viewOf} texts={texts} onRetry={retry} />
            </div>
            {geo && layout && (
              <PageChrome
                geo={geo}
                layout={layout}
                first={first}
                book={book.title}
                items={items}
                viewOf={viewOf}
              />
            )}
          </div>
          {cols === 2 && <div className="gutter" style={{ left: geo?.pw }} />}
        </section>
        {geo && layout && (
          <>
            <Edges geo={geo} layout={layout} first={first} onTurn={turn} />
            <Pager geo={geo} layout={layout} first={first} phone={phone} onTurn={turn} />
          </>
        )}
      </div>
      <div className="sr-only" role="status">
        {spreads.announced}
      </div>
      <ReadingControls />
      <BookmarkButton
        current={ready ? currentKey : null}
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
        current={ready ? currentKey : null}
        phone={phone}
        width={width}
        versions={versions}
        flow={flow}
        onGo={onJump}
        onKeep={(quote) => {
          if (quote.itemKey) void marks.save(book.id, quote.itemKey, quote.text)
        }}
      />
    </div>
  )
}

type BookPagesProps = {
  items: TocItem[]
  viewOf: (key: string) => ItemView
  texts: ReadonlyMap<string, string>
  onRetry: (key: string) => void
}

/** The flow's items in order; rendered again only when what they show changes, not on a turn. */
const BookPages = memo(function BookPages({ items, viewOf, texts, onRetry }: BookPagesProps) {
  return (
    <>
      {items.map((item, i) => (
        <ItemArticle
          key={item.itemKey}
          unit={i}
          number={item.number}
          title={item.title}
          view={viewOf(item.itemKey)}
          markdown={texts.get(item.itemKey) ?? null}
          onRetry={() => onRetry(item.itemKey)}
        />
      ))}
    </>
  )
})

function bookStyle(geo: Geometry | null, ready: boolean): CSSProperties {
  if (!geo) return { visibility: 'hidden' }
  return {
    left: geo.left,
    top: geo.top,
    width: geo.cols * geo.pw,
    height: geo.ph,
    visibility: ready ? undefined : 'hidden',
  }
}

function flowStyle(geo: Geometry | null, size: number, line: number): CSSProperties | undefined {
  if (!geo) return undefined
  const gap = geo.pw - geo.tw
  return {
    fontSize: size,
    lineHeight: `${line}px`,
    left: geo.px,
    top: geo.pt,
    height: geo.th,
    width: geo.cols * geo.tw + (geo.cols - 1) * gap,
    columnCount: geo.cols,
    columnGap: gap,
    ['--th' as string]: `${geo.th}px`,
  }
}

/** ← and → turn the page, except while typing, in the sidebar or in the contents. */
function useTurnKeys(latest: RefObject<{ turn: (direction: -1 | 1) => void }>) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const direction = TURN_KEYS[event.key]
      const plain = !event.defaultPrevented && !event.altKey && !event.ctrlKey && !event.metaKey
      const target = event.target instanceof Element ? event.target : null
      if (!direction || !plain || !turnsPageFrom(target)) return
      event.preventDefault()
      latest.current.turn(direction)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [latest])
}

type SwipeHandlers = {
  onDrag: (dx: number) => void
  onTurn: (direction: -1 | 1) => void
  onSpringBack: () => void
}

/** A sideways drag follows the finger and turns past [SWIPE]; a scroll or a selection does not. */
function useSwipe({ onDrag, onTurn, onSpringBack }: SwipeHandlers) {
  const swipe = useRef<{ x: number; y: number; dx: number; drag: boolean } | null>(null)
  function onTouchStart(event: TouchEvent) {
    const touch = event.touches[0]
    swipe.current =
      event.touches.length === 1 && touch
        ? { x: touch.clientX, y: touch.clientY, dx: 0, drag: false }
        : null
  }
  function onTouchMove(event: TouchEvent) {
    const s = swipe.current
    const touch = event.touches[0]
    if (!s || !touch) return
    s.dx = touch.clientX - s.x
    const across = Math.abs(s.dx) > 10 && Math.abs(s.dx) > Math.abs(touch.clientY - s.y)
    if (across && getSelection()?.isCollapsed !== false) s.drag = true
    if (s.drag) onDrag(s.dx)
  }
  function onTouchEnd() {
    const s = swipe.current
    swipe.current = null
    if (!s?.drag) return
    if (Math.abs(s.dx) > SWIPE) onTurn(s.dx < 0 ? 1 : -1)
    else onSpringBack()
  }
  return { onTouchStart, onTouchMove, onTouchEnd, onTouchCancel: onTouchEnd }
}
