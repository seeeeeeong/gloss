'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react'
import type { Me } from '@/lib/session'
import { Icon } from './Icon'
import { ShellPanelContext } from './ShellPanel'
import { ShellTitleContext } from './ShellTitle'
import { Tooltip } from './Tooltip'

export type ShelfBook = { id: string; title: string }

// The width below which the sidebar is a drawer, as in globals.css.
const PHONE = '(max-width: 767px)'

const NEW_BOOK = '/books/new'
const OPERATOR = '/admin/accounts'
const ACCOUNT = '/me'

type Props = { me: Me; books: ShelfBook[]; children: ReactNode }

export function AppShell({ me, books, children }: Props) {
  const path = usePathname()
  const [collapsed, setCollapsed] = useState(false)
  const [drawer, setDrawer] = useState(false)
  const [panelSlot, setPanelSlot] = useState<HTMLElement | null>(null)
  const [panelOpen, setPanelOpen] = useState(false)
  const panel = useMemo(() => ({ slot: panelSlot, setOpen: setPanelOpen }), [panelSlot])
  const [pillSlot, setPillSlot] = useState<HTMLElement | null>(null)
  const [barSlot, setBarSlot] = useState<HTMLElement | null>(null)
  const title = useMemo(() => ({ pill: pillSlot, bar: barSlot }), [pillSlot, barSlot])
  const opener = useRef<HTMLButtonElement>(null)
  const firstRow = useRef<HTMLAnchorElement>(null)
  // Escape and the scrim hand focus back to the menu button; following a link leaves it on the new page.
  const returnFocus = useRef(false)
  const operator = me.role === 'operator'
  const current = (href: string) => (path === href ? 'page' : undefined)
  const within = (href: string) =>
    path === href || path.startsWith(`${href}/`) ? 'page' : undefined

  // An open drawer takes the keyboard: focus moves into it and Escape closes it.
  useEffect(() => {
    if (!drawer) {
      // Only now is the main area no longer inert, so the menu button can take focus.
      if (returnFocus.current) opener.current?.focus()
      returnFocus.current = false
      return
    }
    firstRow.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      returnFocus.current = true
      setDrawer(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [drawer])

  // A drawer left open while the viewport widens would keep the main area inert.
  useEffect(() => {
    const phone = matchMedia(PHONE)
    const onChange = () => {
      if (!phone.matches) setDrawer(false)
    }
    phone.addEventListener('change', onChange)
    return () => phone.removeEventListener('change', onChange)
  }, [])

  function dismiss() {
    returnFocus.current = true
    setDrawer(false)
  }

  // The same button closes the drawer on a phone and folds the sidebar to its rail elsewhere.
  function hide() {
    if (matchMedia(PHONE).matches) dismiss()
    else setCollapsed(true)
  }

  const leave = () => setDrawer(false)
  const frame = [
    'app',
    (collapsed || panelOpen) && 'rail',
    panelOpen && 'panel-open',
    drawer && 'drawer',
  ]
    .filter(Boolean)
    .join(' ')
  return (
    <div className={frame}>
      <nav id="sidebar" className="sb" aria-label="사이드바">
        <div className="sb-full">
          <div className="sb-head">
            <Link className="wordmark" href="/" onClick={leave}>
              Gloss
            </Link>
            <button
              type="button"
              className="ibtn tert"
              aria-label="사이드바 닫기"
              data-tip="사이드바 닫기"
              onClick={hide}
            >
              <Icon name="sidebar" />
            </button>
          </div>
          <div className="sb-scroll">
            <Link
              ref={firstRow}
              className="row"
              href={NEW_BOOK}
              aria-current={current(NEW_BOOK)}
              onClick={leave}
            >
              <Icon name="newbook" />
              <span className="lbl-t">새 책</span>
            </Link>
            <div className="sb-sec" id="shelf">
              책장
            </div>
            <ul aria-labelledby="shelf">
              {books.map((book) => (
                <li key={book.id}>
                  <Link
                    className="row"
                    href={`/books/${book.id}`}
                    aria-current={within(`/books/${book.id}`)}
                    onClick={leave}
                  >
                    <span className="lbl-t">{book.title}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </div>
          <div className="sb-foot">
            {operator && (
              <Link className="row" href={OPERATOR} aria-current={within(OPERATOR)} onClick={leave}>
                <Icon name="shield" />
                <span className="lbl-t">운영자</span>
              </Link>
            )}
            <Link className="row" href={ACCOUNT} aria-current={current(ACCOUNT)} onClick={leave}>
              <Icon name="account" />
              <span className="lbl-t">마이페이지</span>
            </Link>
          </div>
        </div>
        <div className="sb-rail">
          <button
            type="button"
            className="ibtn tert first"
            aria-label="사이드바 열기"
            data-tip="사이드바 열기"
            onClick={() => setCollapsed(false)}
          >
            <Icon name="sidebar" />
          </button>
          <Link
            className="ibtn"
            href={NEW_BOOK}
            aria-label="새 책"
            data-tip="새 책"
            aria-current={current(NEW_BOOK)}
          >
            <Icon name="newbook" />
          </Link>
          <button
            type="button"
            className="ibtn"
            aria-label="책장"
            data-tip="책장"
            onClick={() => setCollapsed(false)}
          >
            <Icon name="book" />
          </button>
          <span className="rail-gap" />
          {operator && (
            <Link
              className="ibtn round"
              href={OPERATOR}
              aria-label="운영자"
              data-tip="운영자"
              aria-current={within(OPERATOR)}
            >
              <Icon name="shield" />
            </Link>
          )}
          <Link
            className="ibtn round"
            href={ACCOUNT}
            aria-label="마이페이지"
            data-tip="마이페이지"
            aria-current={current(ACCOUNT)}
          >
            <Icon name="account" />
          </Link>
        </div>
      </nav>
      <button
        type="button"
        className="scrim"
        aria-label="사이드바 닫기"
        tabIndex={-1}
        onClick={dismiss}
      />
      <main className="main" inert={drawer}>
        <div className="top">
          <div className="top-pill">
            <button
              ref={opener}
              type="button"
              className="ibtn round"
              aria-label="사이드바 열기"
              aria-expanded={drawer}
              aria-controls="sidebar"
              onClick={() => setDrawer(true)}
            >
              <Icon name="menu" />
            </button>
            <span ref={setPillSlot} className="top-slot" />
            <Link className="name" href="/">
              Gloss
            </Link>
          </div>
          <span ref={setBarSlot} className="top-slot" />
          <Link className="top-title" href="/">
            Gloss
          </Link>
          <Link className="ibtn round top-new" href={NEW_BOOK} aria-label="새 책">
            <Icon name="newbook" />
          </Link>
        </div>
        <ShellPanelContext value={panel}>
          <ShellTitleContext value={title}>{children}</ShellTitleContext>
        </ShellPanelContext>
      </main>
      <aside ref={setPanelSlot} className="panel" hidden={!panelOpen} />
      <Tooltip />
    </div>
  )
}
