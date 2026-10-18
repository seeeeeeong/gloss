'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { Me } from '@/lib/session'
import { toneOf } from '@/lib/shelf'
import { type Crumb, crumbOf } from '@/lib/shell'
import { Icon } from './Icon'
import { ShelfSearch } from './ShelfSearch'
import { ShellActions } from './ShellActions'
import { type PanelKind, ShellPanelContext } from './ShellPanel'
import { ShellTitleContext } from './ShellTitle'
import { Tooltip } from './Tooltip'
import { useDarkTheme } from './useDarkTheme'

export type ShelfBook = { id: string; title: string }

// The width below which the sidebar is a drawer, as in globals.css.
const PHONE = '(max-width: 767px)'

const SHELF = '/'
const NEW_BOOK = '/books/new'
const OPERATOR = '/admin/accounts'
const ACCOUNT = '/me'
// A book's own page, where the reader puts its title and tools in the header.
const READING = /^\/books\/(?!new$)[^/]+$/

type Props = { me: Me; books: ShelfBook[]; children: ReactNode }

export function AppShell({ me, books, children }: Props) {
  const path = usePathname()
  const [drawer, setDrawer] = useState(false)
  const [search, setSearch] = useState(false)
  const reading = READING.test(path)
  const [panelSlot, setPanelSlot] = useState<HTMLElement | null>(null)
  const [panelOpen, setPanelOpen] = useState<PanelKind | null>(null)
  const setOpen = useCallback(
    (open: boolean, kind: PanelKind = 'panel') => setPanelOpen(open ? kind : null),
    [],
  )
  const panel = useMemo(
    () => ({ slot: panelSlot, open: panelOpen !== null, setOpen }),
    [panelSlot, panelOpen, setOpen],
  )
  const [pillSlot, setPillSlot] = useState<HTMLElement | null>(null)
  const [barSlot, setBarSlot] = useState<HTMLElement | null>(null)
  const [actsSlot, setActsSlot] = useState<HTMLElement | null>(null)
  const title = useMemo(
    () => ({ pill: pillSlot, bar: barSlot, acts: actsSlot }),
    [pillSlot, barSlot, actsSlot],
  )
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
      // Escape in the search over the drawer is the search's, which closes the drawer itself.
      const inDialog = event.target instanceof Element && event.target.closest('dialog') !== null
      if (event.key !== 'Escape' || inDialog) return
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

  const leave = () => setDrawer(false)
  function closeSearch() {
    setSearch(false)
    if (drawer) dismiss()
  }
  const wide = panelOpen === 'panel'
  const crumb = crumbOf(path)
  const frame = [
    'app',
    (wide || reading) && 'rail',
    wide && 'panel-open',
    panelOpen === 'tutor' && 'tutor-open',
    drawer && 'drawer',
  ]
    .filter(Boolean)
    .join(' ')
  return (
    <div className={frame}>
      <nav id="sidebar" className="sb" aria-label="사이드바">
        <div className="sb-full">
          <div className="sb-head">
            <Link className="brand" href={SHELF} aria-label="Gloss 내 서재" onClick={leave}>
              gloss
              <span className="brand-mark" aria-hidden="true">
                ↗
              </span>
            </Link>
            <button
              type="button"
              className="ibtn tert sb-close"
              aria-label="사이드바 닫기"
              onClick={dismiss}
            >
              <Icon name="x" />
            </button>
          </div>
          <div className="sb-scroll">
            <div className="sb-cap">개인 서재</div>
            <nav className="sb-nav" aria-label="주 메뉴">
              <Link
                ref={firstRow}
                className="row"
                href={SHELF}
                aria-current={current(SHELF)}
                onClick={leave}
              >
                <Icon name="library" />
                <span className="lbl-t">내 서재</span>
              </Link>
              <Link
                className="row"
                href={NEW_BOOK}
                aria-current={current(NEW_BOOK)}
                onClick={leave}
              >
                <Icon name="plus" />
                <span className="lbl-t">책 추가</span>
              </Link>
              {reading && (
                <button type="button" className="row" onClick={() => setSearch(true)}>
                  <Icon name="search" />
                  <span className="lbl-t">서재 검색</span>
                </button>
              )}
            </nav>
            <div className="sb-sec" id="shelf">
              내 책<span>{books.length}</span>
            </div>
            <ul className="sb-books" aria-labelledby="shelf">
              {books.map((book) => (
                <li key={book.id}>
                  <Link
                    className="row"
                    href={`/books/${book.id}`}
                    aria-current={within(`/books/${book.id}`)}
                    onClick={leave}
                  >
                    <span className={`spine tone-${toneOf(book.id)}`} aria-hidden="true" />
                    <span className="lbl-t">{book.title}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </div>
          <div className="sb-foot">
            {reading && <ThemeRow />}
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
        <Rail operator={operator} within={within} onSearch={() => setSearch(true)} />
      </nav>
      <button
        type="button"
        className="scrim"
        aria-label="사이드바 닫기"
        tabIndex={-1}
        onClick={dismiss}
      />
      <main className="main" inert={drawer}>
        <div className={reading ? 'top reading' : 'top'}>
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
            <Link className="name" href={SHELF}>
              Gloss
            </Link>
          </div>
          <span ref={setBarSlot} className="top-slot" />
          {crumb && <Crumbs crumb={crumb} />}
          <div ref={setActsSlot} className="top-acts" />
          {!reading && <ShellActions name={me.displayName} />}
          <Link className="ibtn round top-new" href={NEW_BOOK} aria-label="책 추가">
            <Icon name="plus" />
          </Link>
        </div>
        <ShellPanelContext value={panel}>
          <ShellTitleContext value={title}>{children}</ShellTitleContext>
        </ShellPanelContext>
      </main>
      <aside ref={setPanelSlot} className="panel" hidden={panelOpen === null} />
      {search && <ShelfSearch books={books} onClose={closeSearch} />}
      <Tooltip />
    </div>
  )
}

type RailProps = {
  operator: boolean
  within: (href: string) => 'page' | undefined
  onSearch: () => void
}

/** The sidebar folded to icons, beside a book or an open panel: the shelf, a search and the theme. */
function Rail({ operator, within, onSearch }: RailProps) {
  const { dark, toggle } = useDarkTheme()
  const themeLabel = dark ? '라이트 모드로 변경' : '다크 모드로 변경'
  return (
    <div className="sb-rail">
      <Link className="brand sm first" href={SHELF} aria-label="Gloss 내 서재">
        g<span className="brand-mark">.</span>
      </Link>
      <Link className="ibtn" href={SHELF} aria-label="내 서재" data-tip="내 서재">
        <Icon name="library" />
      </Link>
      <button
        type="button"
        className="ibtn"
        aria-label="서재 검색"
        data-tip="검색"
        onClick={onSearch}
      >
        <Icon name="search" />
      </button>
      <span className="rail-gap" />
      <button
        type="button"
        className="ibtn"
        aria-label={themeLabel}
        data-tip={themeLabel}
        onClick={toggle}
      >
        <Icon name={dark ? 'sun' : 'moon'} />
      </button>
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
      <Link className="ibtn round" href={ACCOUNT} aria-label="마이페이지" data-tip="마이페이지">
        <Icon name="account" />
      </Link>
    </div>
  )
}

/** The rail's theme switch as a row, for a phone's drawer beside a book, where the rail is folded away. */
function ThemeRow() {
  const { dark, toggle } = useDarkTheme()
  return (
    <button type="button" className="row" onClick={toggle}>
      <Icon name={dark ? 'sun' : 'moon'} />
      <span className="lbl-t">{dark ? '라이트 모드로 변경' : '다크 모드로 변경'}</span>
    </button>
  )
}

/** Where the screen sits: under the shelf, or on its own. */
function Crumbs({ crumb }: { crumb: Crumb }) {
  return (
    <p className="crumbs">
      {crumb.shelf && (
        <>
          <span>서재</span>
          <span aria-hidden="true">/</span>
        </>
      )}
      <span className="here">{crumb.name}</span>
    </p>
  )
}
