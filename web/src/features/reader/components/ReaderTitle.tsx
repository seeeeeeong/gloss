'use client'

import { type MouseEvent, type RefObject, useCallback, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useShellTitle } from '@/components/layouts/ShellTitle'
import { Icon } from '@/components/ui/Icon'
import { type ItemView, menuRowsOf } from '@/features/reader/utils/reader'
import type { TocItem } from '@/lib/books'
import { headingOf } from '@/lib/posts'
import { MENU_ID, ReaderMenu } from './ReaderMenu'
import type { ReaderBook } from './ReaderPages'

type TitleProps = {
  book: ReaderBook
  items: TocItem[]
  current: string | null
  phone: boolean
  viewOf: (key: string) => ItemView
  onGo: (key: string) => void
}

/** The item open, or the book on its front pages, in the shell's header; it opens the contents. */
export function ReaderTitle({ book, items, current, phone, viewOf, onGo }: TitleProps) {
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
