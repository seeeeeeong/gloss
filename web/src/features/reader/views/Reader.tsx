'use client'

import './reader.css'
import {
  type CSSProperties,
  type FocusEvent,
  type MouseEvent,
  memo,
  type RefObject,
  type TouchEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { createPortal } from 'react-dom'
import { useShellTitle } from '@/components/layouts/ShellTitle'
import { Icon } from '@/components/ui/Icon'
import { Edges, PageChrome, Pager } from '@/features/reader/components/ReaderChrome'
import { MENU_ID, ReaderMenu } from '@/features/reader/components/ReaderMenu'
import {
  Contents,
  Cover,
  ItemArticle,
  type ReaderBook,
} from '@/features/reader/components/ReaderPages'
import { ReadingSizeDialog } from '@/features/reader/components/ReadingSize'
import { Tutor } from '@/features/reader/components/Tutor'
import { useBookPosts } from '@/features/reader/hooks/useBookPosts'
import { useReadingSize } from '@/features/reader/hooks/useReadingSize'
import { useSpreads } from '@/features/reader/hooks/useSpreads'
import { useStageGeometry } from '@/features/reader/hooks/useStageGeometry'
import { dwell } from '@/features/reader/utils/dwell'
import {
  COVER,
  type ContentsRow,
  contentsRowsOf,
  type Geometry,
  type ItemView,
  itemsAround,
  menuRowsOf,
  openItemOf,
  readingItemOf,
  rowMarkOf,
  turnsPageFrom,
  unitOfItem,
  visibleUnits,
} from '@/features/reader/utils/reader'
import { lineOf } from '@/features/reader/utils/reading'
import { useBookmarks } from '@/hooks/useBookmarks'
import { type Bookmark, itemMarkOf } from '@/lib/bookmarks'
import type { TocItem } from '@/lib/books'
import { headingOf, type PostSummary, readerHref } from '@/lib/posts'
import { RECENT_KEY, readsFrom, withRead } from '@/lib/shelf'

// A post is asked for only once the reader has stayed on its item this long: each is paid for.
const REST_MS = 1500
// A swipe shorter than this springs back.
const SWIPE = 40
const TURN_KEYS: Record<string, -1 | 1> = {
  ArrowLeft: -1,
  PageUp: -1,
  ArrowRight: 1,
  PageDown: 1,
}

type Props = {
  book: ReaderBook
  items: TocItem[]
  posts: PostSummary[]
  start: string | null
  /** The bookmarks the reader keeps in this book. */
  bookmarks: Bookmark[]
}

const lastReadKey = (bookId: string) => `gloss.reader.${bookId}.lastRead`

/**
 * A book read page by page: the cover, the 차례 and every item flow through one multi-column
 * box, a column a page, turned a spread at a time. Pages are counted inside their item, since a
 * resize or a newly written item moves every page after it.
 */
export function Reader({ book, items, posts, start, bookmarks }: Props) {
  const index = useMemo(() => new Map(items.map((item, i) => [item.itemKey, i])), [items])
  const { viewOf, texts, versions, arrive, prefetch, retry } = useBookPosts(book.id, posts)
  const stage = useRef<HTMLDivElement>(null)
  const flow = useRef<HTMLDivElement>(null)
  const [size] = useReadingSize()
  const marks = useBookmarks(bookmarks)
  const line = lineOf(size)
  const { geo, phone, width } = useStageGeometry(stage, line)

  const [focus, setFocus] = useState<string | null>(start)
  const spreads = useSpreads(flow, geo, {
    unit: start ? unitOfItem(index.get(start) ?? 0) : COVER,
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
    resting.current?.at(reading === null ? null : keyAt(reading))
    prefetch(itemsAround(visibleUnits(layout, first, cols), items.length).map(keyAt))
  })

  const ready = layout !== null
  const lastRead = useReadingPlace({
    bookId: book.id,
    items,
    index,
    start,
    current: ready ? currentKey : undefined,
  })

  function turn(direction: -1 | 1) {
    if (spreads.turn(direction)) setFocus(null)
  }

  function goItem(key: string) {
    const at = index.get(key)
    if (at === undefined) return
    setFocus(key)
    spreads.open(unitOfItem(at))
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
  // From a 차례 row, the item's heading takes focus, so Tab goes on in the item, not the 차례.
  const heading = useRef<string | null>(null)
  const onGo = useCallback((key: string) => {
    heading.current = key
    latest.current.goItem(key)
  }, [])
  useEffect(() => {
    const at = heading.current === null ? undefined : index.get(heading.current)
    if (!settled.current || at === undefined) return
    heading.current = null
    const el = flow.current?.querySelector<HTMLElement>(`[data-unit="${unitOfItem(at)}"] > h1`)
    el?.focus({ preventScroll: true })
  })
  // From the tutor, a divider goes to its item and leaves focus where it was.
  const onJump = useCallback((key: string) => latest.current.goItem(key), [])
  useTurnKeys(latest)
  const swipe = useSwipe({ onDrag: spreads.follow, onTurn: turn, onSpringBack: spreads.springBack })

  return (
    <div className="reader">
      <ReaderTitle
        book={book}
        items={items}
        current={currentKey}
        phone={phone}
        viewOf={viewOf}
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
              <BookPages
                book={book}
                items={items}
                index={index}
                lastRead={lastRead}
                viewOf={viewOf}
                texts={texts}
                onGo={onGo}
                onRetry={retry}
              />
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
      <BookmarkButton
        current={ready ? currentKey : null}
        marks={marks.marks}
        onSave={(key) => void marks.save(book.id, key, null)}
        onRemove={(mark) => void marks.remove(mark)}
      />
      <SizeButton />
      <div className={marks.said ? 'toast shown' : 'toast'} role="status">
        {marks.said}
      </div>
      <Tutor
        key={book.id}
        bookId={book.id}
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
  book: ReaderBook
  items: TocItem[]
  index: ReadonlyMap<string, number>
  lastRead: string | null
  viewOf: (key: string) => ItemView
  texts: ReadonlyMap<string, string>
  onGo: (key: string) => void
  onRetry: (key: string) => void
}

/** The flow's units in order; rendered again only when what they show changes, not on a turn. */
const BookPages = memo(function BookPages(props: BookPagesProps) {
  const { book, items, index, lastRead, viewOf, texts, onGo, onRetry } = props
  const rows = useMemo(() => contentsRowsOf(items, lastRead), [items, lastRead])
  const through = lastRead === null ? -1 : (index.get(lastRead) ?? -1)
  const isRead = (key: string) =>
    viewOf(key).kind === 'written' && (index.get(key) ?? Number.POSITIVE_INFINITY) <= through
  const markOf = (row: ContentsRow) => rowMarkOf(row, viewOf, isRead)
  return (
    <>
      <Cover book={book} />
      <Contents bookId={book.id} rows={rows} markOf={markOf} onGo={onGo} />
      {items.map((item, i) => (
        <ItemArticle
          key={item.itemKey}
          unit={unitOfItem(i)}
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
function useReadingPlace({ bookId, items, index, start, current }: PlaceArgs): string | null {
  const [lastRead, setLastRead] = useState<string | null>(start)

  useEffect(() => {
    if (start) return
    try {
      const stored = localStorage.getItem(lastReadKey(bookId))
      if (stored && index.has(stored)) setLastRead(stored)
    } catch {}
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

type TitleProps = {
  book: ReaderBook
  items: TocItem[]
  current: string | null
  phone: boolean
  viewOf: (key: string) => ItemView
  onGo: (key: string) => void
}

/** The item open, or the book on its front pages, in the shell's header; it opens the contents. */
function ReaderTitle({ book, items, current, phone, viewOf, onGo }: TitleProps) {
  const shell = useShellTitle()
  const bar = useRef<HTMLButtonElement>(null)
  const pill = useRef<HTMLButtonElement>(null)
  const [menu, setMenu] = useState<{ left: number | null; focus: boolean } | null>(null)
  const rows = useMemo(() => menuRowsOf(items), [items])
  const item = items.find((it) => it.itemKey === current)
  const label = item ? headingOf(item) : book.title

  const close = useCallback(
    (refocus: boolean) => {
      setMenu(null)
      if (refocus) (phone ? pill : bar).current?.focus({ preventScroll: true })
    },
    [phone],
  )
  function toggle(event: MouseEvent<HTMLButtonElement>) {
    if (menu) {
      setMenu(null)
      return
    }
    const button = event.currentTarget
    const main = button.closest('.main')?.getBoundingClientRect()
    const left = phone || !main ? null : button.getBoundingClientRect().left - main.left
    setMenu({ left, focus: event.detail === 0 })
  }
  function go(key: string) {
    close(true)
    onGo(key)
  }

  const button = (className: string, ref: RefObject<HTMLButtonElement | null>) => (
    <button
      ref={ref}
      type="button"
      className={className}
      aria-haspopup="menu"
      aria-expanded={menu !== null}
      aria-controls={MENU_ID}
      data-contents-trigger=""
      onClick={toggle}
    >
      <span>{label}</span>
      <Icon name="chevD" size={16} />
    </button>
  )
  return (
    <>
      {shell.bar && createPortal(button('top-conv', bar), shell.bar)}
      {shell.pill && createPortal(button('name', pill), shell.pill)}
      {menu && (
        <ReaderMenu
          bookId={book.id}
          rows={rows}
          current={current}
          viewOf={viewOf}
          left={menu.left}
          focusCurrent={menu.focus}
          onGo={go}
          onClose={close}
        />
      )}
    </>
  )
}

/** 글자 크기 in the header, before the tutor's toggle. */
function SizeButton() {
  const shell = useShellTitle()
  const [open, setOpen] = useState(false)
  if (!shell.acts) return null
  return createPortal(
    <>
      <button
        type="button"
        className="ibtn"
        aria-label="글자 크기"
        data-tip="글자 크기"
        onClick={() => setOpen(true)}
      >
        <Icon name="text" />
      </button>
      {open && <ReadingSizeDialog onClose={() => setOpen(false)} />}
    </>,
    shell.acts,
  )
}

type MarkProps = {
  current: string | null
  marks: Bookmark[]
  onSave: (itemKey: string) => void
  onRemove: (mark: Bookmark) => void
}

/** The item open kept whole as a bookmark, or let go, from the header; nothing to keep on the front pages. */
function BookmarkButton({ current, marks, onSave, onRemove }: MarkProps) {
  const shell = useShellTitle()
  if (!shell.acts) return null
  const own = itemMarkOf(marks, current)
  const label = own ? '책갈피 지우기' : '이 글 책갈피에 저장'
  return createPortal(
    <button
      type="button"
      className={own ? 'ibtn saved' : 'ibtn'}
      aria-pressed={own !== undefined}
      aria-label={label}
      data-tip={label}
      disabled={current === null}
      onClick={() => {
        if (own) onRemove(own)
        else if (current) onSave(current)
      }}
    >
      <Icon name="bookmark" />
    </button>,
    shell.acts,
  )
}
