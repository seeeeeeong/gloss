'use client'

import { type KeyboardEvent, type MouseEvent, useEffect, useRef } from 'react'
import { readerHref } from '../lib/posts'
import type { ItemView, MenuRow } from '../lib/reader'
import { Icon } from './Icon'

export const MENU_ID = 'reader-contents'

type Props = {
  bookId: string
  rows: MenuRow[]
  current: string | null
  viewOf: (key: string) => ItemView
  /** From the title button's left on a wide screen; null on a phone, where it spans the screen. */
  left: number | null
  /** Opened from the keyboard: the current row takes focus. */
  focusCurrent: boolean
  onGo: (key: string) => void
  onClose: (refocus: boolean) => void
}

const STEPS: Record<string, (at: number, last: number) => number> = {
  ArrowDown: (at, last) => Math.min(last, at + 1),
  ArrowUp: (at) => Math.max(0, at - 1),
  Home: () => 0,
  End: (_, last) => last,
}

/** The book's contents under the header's title: every item, the current one checked. */
export function ReaderMenu(props: Props) {
  const { bookId, rows, current, viewOf, left, focusCurrent, onGo, onClose } = props
  const box = useRef<HTMLDivElement>(null)

  // The current row, or the first on the cover and the 차례, is the one Tab and the keyboard start from.
  useEffect(() => {
    const el = box.current
    const row = el?.querySelector<HTMLElement>('[tabindex="0"]')
    if (!el || !row) return
    el.scrollTop = Math.max(0, row.offsetTop - el.clientHeight / 2 + 18)
    if (focusCurrent) row.focus({ preventScroll: true })
  }, [focusCurrent])

  // Escape, a press outside or focus leaving it closes the menu; a title button toggles it itself.
  useEffect(() => {
    const outside = (target: EventTarget | null) =>
      target instanceof Node &&
      !box.current?.contains(target) &&
      !(target instanceof Element && target.closest('[data-contents-trigger]'))
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') onClose(true)
    }
    const onPress = (event: globalThis.MouseEvent) => {
      if (outside(event.target)) onClose(false)
    }
    const onFocus = (event: FocusEvent) => {
      if (outside(event.target)) onClose(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onPress)
    document.addEventListener('focusin', onFocus)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onPress)
      document.removeEventListener('focusin', onFocus)
    }
  }, [onClose])

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = STEPS[event.key]
    if (!step || !box.current) return
    event.preventDefault()
    const all = [...box.current.querySelectorAll<HTMLElement>('[role="menuitem"]')]
    const at = all.indexOf(document.activeElement as HTMLElement)
    all[step(at, all.length - 1)]?.focus()
  }

  const go = (key: string) => (event: MouseEvent<HTMLAnchorElement>) => {
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
    event.preventDefault()
    onGo(key)
  }

  return (
    <div
      ref={box}
      id={MENU_ID}
      className="tocpop"
      role="menu"
      aria-label="목차"
      style={left === null ? undefined : { left }}
      onKeyDown={onKeyDown}
    >
      {rows.map((row, i) => (
        <MenuItem
          key={row.key}
          href={readerHref(bookId, row.key)}
          row={row}
          here={row.key === current}
          start={row.key === current || (i === 0 && !rows.some((r) => r.key === current))}
          view={viewOf(row.key)}
          onClick={go(row.key)}
        />
      ))}
    </div>
  )
}

type ItemProps = {
  href: string
  row: MenuRow
  here: boolean
  /** The one row in the tab order: the menu's keyboard starts here. */
  start: boolean
  view: ItemView
  onClick: (event: MouseEvent<HTMLAnchorElement>) => void
}

function MenuItem({ href, row, here, start, view, onClick }: ItemProps) {
  const cls = row.heading ? 'tp-ch' : 'tp-row'
  return (
    <a
      role="menuitem"
      className={!here && view.kind !== 'written' ? `${cls} todo` : cls}
      href={href}
      aria-current={here ? 'page' : undefined}
      tabIndex={start ? 0 : -1}
      onClick={onClick}
    >
      <span>{row.label}</span>
      {here ? (
        <Icon name="check" size={16} />
      ) : (
        view.kind === 'writing' && <span className="meta">쓰는 중</span>
      )}
    </a>
  )
}
