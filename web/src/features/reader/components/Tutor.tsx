'use client'

import './tutor.css'
import {
  type CSSProperties,
  type PointerEvent,
  type ReactNode,
  type RefObject,
  type UIEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react'
import { createPortal } from 'react-dom'
import { useShellPanel } from '@/components/layouts/ShellPanel'
import { useShellTitle } from '@/components/layouts/ShellTitle'
import { Icon } from '@/components/ui/Icon'
import { itemKeyFor, type Quote, quoteOf } from '@/features/reader/api/chat'
import type { useTutor } from '@/features/reader/hooks/useTutor'
import {
  holdOthers,
  isQuiet,
  narrowAt,
  pillTop,
  promptOf,
  type SheetSize,
  sheetAfterDrag,
  showsThread,
  startsDocked,
} from '@/features/reader/utils/tutor'
import type { TocItem } from '@/lib/books'
import { headingOf } from '@/lib/posts'
import { TutorComposer } from './TutorComposer'
import { TutorThread } from './TutorThread'

type Props = {
  /** The book's conversation, which the page reads once for the tutor and the contents. */
  tutor: ReturnType<typeof useTutor>
  items: TocItem[]
  /** The item the reader is on; null before the book is laid out. */
  current: string | null
  phone: boolean
  /** The version of each item's post the page shows. */
  versions: ReadonlyMap<string, string>
  /** The stage's width, which decides whether the tutor docks beside the book or opens as a sheet. */
  width: number
  /** The book's flow, where a phrase picked can be asked about. */
  flow: RefObject<HTMLElement | null>
  onGo: (itemKey: string) => void
  /** Keeps a phrase picked in the book as a bookmark. */
  onKeep: (quote: Quote) => void
}

const DOCKED_KEY = 'gloss.tutor.docked'

/**
 * The tutor beside the book: docked in the shell's panel, opened and closed from the header, or a
 * sheet behind a floating button on a phone. A phrase picked in the book can be asked about.
 */
export function Tutor(props: Props) {
  const { tutor, items, current, phone: phoneWide, width, versions, flow, onGo, onKeep } = props
  const { slot, setOpen } = useShellPanel()
  const shell = useShellTitle()
  const docking = useDocked(width > 0)
  const { open } = docking
  const [sheet, setSheet] = useState(false)
  const [quote, setQuote] = useState<Quote | null>(null)
  const [draft, setDraft] = useState('')
  const input = useRef<HTMLTextAreaElement>(null)
  const fab = useRef<HTMLButtonElement>(null)
  const toggle = useRef<HTMLButtonElement>(null)
  // A window too narrow for the book beside the tutor gets the sheet, as a phone does.
  const [narrow, setNarrow] = useState(false)
  const phone = phoneWide || narrow
  const docked = !phone && open
  // Judged only when a new width is measured, against whether the tutor was docked as it was taken.
  const measured = useRef(0)
  useEffect(() => {
    const judged = narrowAt(measured.current, width, docked)
    measured.current = width
    if (judged !== null) setNarrow(judged)
  }, [width, docked])

  useEffect(() => {
    setOpen(docked, 'tutor')
  }, [docked, setOpen])
  useEffect(() => () => setOpen(false), [setOpen])

  // A turn the server refused gives the reader their words back.
  const { refused } = tutor
  useEffect(() => {
    if (!refused) return
    setDraft((typed) => typed || refused.text)
    setQuote((picked) => picked ?? refused.quote)
  }, [refused])

  const phoneRoot = useRef<HTMLDivElement>(null)
  const closeSheet = useCallback(() => setSheet(false), [])
  const sheetSize = useSheetSize(sheet && phone, closeSheet)
  // An open sheet is modal: the page behind it is inert, and closing it hands focus back to its button.
  useEffect(() => {
    if (!sheet || !phone) return
    return holdOthers(
      Array.from(document.body.children) as HTMLElement[],
      (el) => el === phoneRoot.current,
      () => fab.current?.focus({ preventScroll: true }),
    )
  }, [sheet, phone])
  useEffect(() => {
    if (!sheet || !phone) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !event.defaultPrevented) closeSheet()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [sheet, phone, closeSheet])

  // Focus goes to the composer once it is on screen: a panel or a sheet just opened renders first.
  const focusing = useRef(false)
  useEffect(() => {
    if (!focusing.current || !input.current) return
    focusing.current = false
    input.current.focus({ preventScroll: true })
  })
  function focusInput() {
    focusing.current = true
  }

  function send() {
    const text = draft.trim()
    if (!text || tutor.live) return
    const itemKey = itemKeyFor(quote, current, tutor.messages, items)
    void tutor.ask({
      text,
      quote,
      itemKey,
      postVersionId: itemKey ? (versions.get(itemKey) ?? null) : null,
    })
    setDraft('')
    setQuote(null)
  }

  function take(picked: Quote) {
    focusInput()
    setQuote(picked)
    tutor.dismissRefusal()
    if (phone) setSheet(true)
    else docking.show()
  }

  function go(itemKey: string) {
    if (phone) setSheet(false)
    onGo(itemKey)
  }

  const keyOfUnit = useCallback((unit: number) => items[unit]?.itemKey ?? null, [items])

  function closeDocked() {
    docking.choose(false)
    toggle.current?.focus()
  }

  const item = items.find((it) => it.itemKey === current)
  const panel = (sheetHeading: boolean) => (
    <TutorPanel
      tutor={tutor}
      items={items}
      item={item ? headingOf(item) : null}
      grab={sheetHeading}
      onGo={go}
      onClose={sheetHeading ? closeSheet : closeDocked}
      composer={
        <TutorComposer
          value={draft}
          onChange={setDraft}
          quote={quote}
          onUnquote={() => {
            setQuote(null)
            input.current?.focus()
          }}
          placeholder={promptOf(quote, tutor.messages)}
          answering={tutor.live !== null}
          onSend={send}
          onStop={() => void tutor.stop()}
          input={input}
        />
      }
    />
  )
  const toggleLabel = open ? '튜터 닫기' : '튜터 열기'
  return (
    <>
      {shell.acts &&
        !phone &&
        createPortal(
          <button
            ref={toggle}
            type="button"
            className="ibtn tg"
            aria-pressed={open}
            aria-label={toggleLabel}
            data-tip={toggleLabel}
            onClick={() => docking.choose(!open)}
          >
            <Icon name="chat" />
          </button>,
          shell.acts,
        )}
      {docked && slot && createPortal(panel(false), slot)}
      {phone &&
        createPortal(
          <div ref={phoneRoot} className="t-phone">
            <button
              ref={fab}
              inert={sheet}
              type="button"
              className="t-fab"
              aria-label="튜터"
              aria-expanded={sheet}
              onClick={() => {
                focusInput()
                setSheet(true)
              }}
            >
              <Icon name="chat" />
            </button>
            <button
              type="button"
              className={sheet ? 't-scrim open' : 't-scrim'}
              aria-label="튜터 닫기"
              tabIndex={-1}
              onClick={closeSheet}
            />
            <section
              ref={sheetSize.ref}
              className={sheetClass(sheet, sheetSize.size)}
              style={sheetSize.style}
              role="dialog"
              aria-modal="true"
              aria-label="튜터"
              inert={!sheet}
              {...sheetSize.handlers}
            >
              {panel(true)}
            </section>
          </div>,
          document.body,
        )}
      <AskPill flow={flow} keyOfUnit={keyOfUnit} onAsk={take} onKeep={onKeep} />
    </>
  )
}

type PanelProps = {
  tutor: ReturnType<typeof useTutor>
  items: TocItem[]
  /** The item open, which the panel is titled with. */
  item: string | null
  grab: boolean
  onGo: (itemKey: string) => void
  onClose: () => void
  composer: ReactNode
}

/**
 * The panel titled with the item open, its thread kept at the newest turn while the reader stays
 * there, and its composer at the foot.
 */
export function TutorPanel(props: PanelProps) {
  const { tutor, items, item, grab, onGo, onClose, composer } = props
  const body = useRef<HTMLDivElement>(null)
  const stick = useRef(true)
  const { messages, live, refused, loadFailed } = tutor

  // biome-ignore lint/correctness/useExhaustiveDependencies: each new piece or turn scrolls to it
  useLayoutEffect(() => {
    const el = body.current
    if (el && stick.current) el.scrollTop = el.scrollHeight
  }, [messages, live, refused])

  // A turn the reader sends is shown, wherever they had scrolled to.
  const asked = live?.asked
  useLayoutEffect(() => {
    if (asked) stick.current = true
  }, [asked])

  function onScroll(event: UIEvent<HTMLDivElement>) {
    const el = event.currentTarget
    stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < STICK_PX
  }

  const shell = (
    <>
      {grab && <div className="t-grab" aria-hidden="true" />}
      <div className="t-head">
        <h2 className="t-title">{item ?? '튜터'}</h2>
        <button
          type="button"
          className="ibtn"
          aria-label="튜터 닫기"
          data-tip="튜터 닫기"
          onClick={onClose}
        >
          <Icon name="x" />
        </button>
      </div>
      <div ref={body} className="t-body" onScroll={onScroll}>
        {isQuiet(tutor) && (
          <div className="t-quiet" aria-hidden="true">
            <span className="brand">
              gloss<span className="brand-mark">↗</span>
            </span>
          </div>
        )}
        {showsThread(tutor) && !isQuiet(tutor) && (
          <TutorThread
            items={items}
            messages={messages ?? []}
            live={live}
            refused={refused}
            loadFailed={loadFailed}
            elsewhere={tutor.elsewhere}
            onGo={onGo}
            onReload={() => void tutor.reload()}
          />
        )}
      </div>
      <div className="t-dock">{composer}</div>
    </>
  )
  return grab ? shell : <div className="tutor">{shell}</div>
}

const sheetClass = (open: boolean, size: SheetSize) =>
  ['tutor t-sheet', open && 'open', size === 'full' && 'full'].filter(Boolean).join(' ')

// Below the window's height by more than this, the visual viewport has a keyboard over it.
const KEYBOARD_PX = 150

/**
 * The phone's sheet: over the lower part of the page, the post above it, at full height once
 * dragged up or once the keyboard opens; dragged down a step, or closed from the lower one. It
 * follows the visual viewport, so the keyboard never covers the composer.
 */
function useSheetSize(open: boolean, onClose: () => void) {
  const ref = useRef<HTMLElement>(null)
  const [size, setSize] = useState<Exclude<SheetSize, 'closed'>>('half')
  const [drag, setDrag] = useState<number | null>(null)
  const from = useRef(0)

  useEffect(() => {
    if (open) return
    setSize('half')
    setDrag(null)
  }, [open])

  useEffect(() => {
    const viewport = window.visualViewport
    const el = ref.current
    if (!open || !viewport || !el) return
    const follow = () => {
      el.style.setProperty('--vv-h', `${viewport.height}px`)
      el.style.setProperty('--vv-bottom', `${innerHeight - viewport.height - viewport.offsetTop}px`)
      if (innerHeight - viewport.height > KEYBOARD_PX) setSize('full')
    }
    follow()
    viewport.addEventListener('resize', follow)
    viewport.addEventListener('scroll', follow)
    return () => {
      viewport.removeEventListener('resize', follow)
      viewport.removeEventListener('scroll', follow)
    }
  }, [open])

  // The grab bar and the head move the sheet; the buttons in the head stay buttons.
  const handlers = {
    onPointerDown(event: PointerEvent<HTMLElement>) {
      const target = event.target as Element
      if (!target.closest('.t-grab, .t-head') || target.closest('button')) return
      from.current = event.clientY
      setDrag(0)
      event.currentTarget.setPointerCapture(event.pointerId)
    },
    onPointerMove(event: PointerEvent<HTMLElement>) {
      if (drag !== null) setDrag(event.clientY - from.current)
    },
    onPointerUp() {
      if (drag === null) return
      const next = sheetAfterDrag(size, drag)
      setDrag(null)
      if (next === 'closed') onClose()
      else setSize(next)
    },
  }
  const style: CSSProperties | undefined =
    drag === null
      ? undefined
      : {
          transition: 'none',
          transform: drag > 0 ? `translateY(${drag}px)` : undefined,
          ['--pull' as string]: `${Math.max(0, -drag)}px`,
        }
  return { ref, size, style, handlers: { ...handlers, onPointerCancel: handlers.onPointerUp } }
}

// Within this of the bottom, a new piece keeps the thread at the bottom.
const STICK_PX = 120

/**
 * Whether the tutor is docked: closed as a book opens unless the reader docked it from the header,
 * a choice kept across books. Opened to ask about a phrase, it stays open for that book only.
 */
function useDocked(laidOut: boolean) {
  const [open, setOpen] = useState(false)
  // Not before the book's first width is judged: it was measured with the tutor closed.
  useEffect(() => {
    if (!laidOut) return
    try {
      if (startsDocked(localStorage.getItem(DOCKED_KEY))) setOpen(true)
    } catch {}
  }, [laidOut])
  const show = useCallback(() => setOpen(true), [])
  const choose = useCallback((next: boolean) => {
    setOpen(next)
    try {
      localStorage.setItem(DOCKED_KEY, String(next))
    } catch {}
  }, [])
  return { open, show, choose }
}

type Pick = Quote & { style: CSSProperties }

const PILL_HALF = 72
const EDGE = 8

// A drag or a phone's selection handles change the selection many times a second; the pill waits
// until it has held still this long, under the 150 ms or so a reader notices, so it is not placed
// again on every change.
const SELECTION_SETTLES_MS = 120

type AskProps = {
  flow: RefObject<HTMLElement | null>
  keyOfUnit: (unit: number) => string | null
  onAsk: (quote: Quote) => void
  onKeep: (quote: Quote) => void
}

/**
 * Over a phrase picked in an item's text: "Gloss에게 묻기", which puts the phrase in the quote row,
 * and a bookmark, which keeps it.
 */
function AskPill({ flow, keyOfUnit, onAsk, onKeep }: AskProps) {
  const [pick, setPick] = useState<Pick | null>(null)

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined
    const place = () => setPick(pickIn(flow.current, keyOfUnit))
    const onChange = () => {
      clearTimeout(timer)
      timer = setTimeout(place, SELECTION_SETTLES_MS)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || getSelection()?.isCollapsed !== false) return
      if (!flow.current?.contains(getSelection()?.anchorNode ?? null)) return
      event.preventDefault()
      getSelection()?.removeAllRanges()
    }
    document.addEventListener('selectionchange', onChange)
    document.addEventListener('keydown', onKey)
    addEventListener('resize', onChange)
    return () => {
      clearTimeout(timer)
      document.removeEventListener('selectionchange', onChange)
      document.removeEventListener('keydown', onKey)
      removeEventListener('resize', onChange)
    }
  }, [flow, keyOfUnit])

  if (!pick) return null
  const quote = { text: pick.text, itemKey: pick.itemKey }
  const done = (then: (quote: Quote) => void) => () => {
    getSelection()?.removeAllRanges()
    setPick(null)
    then(quote)
  }
  return createPortal(
    // Pressing either keeps the selection until the click lands.
    // biome-ignore lint/a11y/noStaticElementInteractions: it only stops the press from dropping the selection
    <div className="ask" style={pick.style} onMouseDown={(event) => event.preventDefault()}>
      <button type="button" className="ask-q" onClick={done(onAsk)}>
        <Icon name="quote" size={16} />
        <span>Gloss에게 묻기</span>
      </button>
      <button
        type="button"
        className="ask-keep"
        aria-label="책갈피에 저장"
        data-tip="책갈피에 저장"
        onClick={done(onKeep)}
      >
        <Icon name="bookmark" size={16} />
      </button>
    </div>,
    document.body,
  )
}

/** The phrase picked inside an item on the open spread, and where its pill goes; null otherwise. */
function pickIn(flow: HTMLElement | null, keyOfUnit: (unit: number) => string | null): Pick | null {
  const selection = getSelection()
  if (!flow || !selection || selection.isCollapsed || selection.rangeCount === 0) return null
  const range = selection.getRangeAt(0)
  const node = range.commonAncestorContainer
  const el = node instanceof Element ? node : node.parentElement
  const article = el?.closest('article.item')
  if (!article || !flow.contains(article) || article.getAttribute('aria-busy') === 'true')
    return null
  const rect = range.getBoundingClientRect()
  const book = flow.closest('.book')?.getBoundingClientRect()
  if (book && (rect.right < book.left || rect.left > book.right)) return null
  const text = quoteOf(selection.toString())
  if (!text) return null
  const coarse = matchMedia('(pointer: coarse)').matches
  const centre = rect.left + rect.width / 2
  const left = Math.max(EDGE + PILL_HALF, Math.min(innerWidth - EDGE - PILL_HALF, centre))
  const top = pillTop(rect, innerHeight, coarse)
  return {
    text,
    itemKey: keyOfUnit(Number(article.getAttribute('data-unit'))),
    style: { left, top, transform: 'translateX(-50%)' },
  }
}
