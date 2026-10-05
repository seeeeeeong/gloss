'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { type ReactNode, useEffect, useRef, useState } from 'react'
import type { Me } from '@/lib/session'
import { Icon } from './Icon'
import { SignOutButton } from './SignOutButton'
import { ThemeToggle } from './ThemeToggle'

const LINKS = [
  { href: '/', label: '내 책장', icon: 'book', operatorOnly: false },
  { href: '/me', label: '마이페이지', icon: 'user', operatorOnly: false },
  { href: '/admin/accounts', label: '계정 관리', icon: 'users', operatorOnly: true },
] as const

export function AppShell({ me, children }: { me: Me; children: ReactNode }) {
  const path = usePathname()
  const [open, setOpen] = useState(false)
  const toggle = useRef<HTMLButtonElement>(null)
  const firstLink = useRef<HTMLAnchorElement>(null)
  // Escape and the scrim hand focus back to the toggle; following a link leaves it on the new page.
  const returnFocus = useRef(false)

  // An open drawer takes the keyboard: focus moves into it and Escape closes it.
  useEffect(() => {
    if (!open) {
      // Only now is the top bar no longer inert, so the toggle can take focus.
      if (returnFocus.current) toggle.current?.focus()
      returnFocus.current = false
      return
    }
    firstLink.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      returnFocus.current = true
      setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open])

  function close() {
    returnFocus.current = true
    setOpen(false)
  }

  return (
    <div className="app" data-drawer={open ? 'open' : undefined}>
      <header className="topbar" inert={open}>
        <button
          ref={toggle}
          type="button"
          className="icon-btn lib-toggle"
          aria-label="메뉴 열기"
          aria-expanded={open}
          aria-controls="library"
          onClick={() => setOpen(true)}
        >
          <Icon name="menu" />
        </button>
        <Link className="brand" href="/">
          <span className="brand-mark" aria-hidden="true">
            G
          </span>
          Gloss
        </Link>
        <div className="top-actions">
          <span className="who">{me.displayName}</span>
          <ThemeToggle />
          <SignOutButton />
        </div>
      </header>
      <nav id="library" className="library" aria-label="주요 메뉴">
        <div className="library-title">Gloss</div>
        {LINKS.filter((link) => !link.operatorOnly || me.role === 'operator').map((link, index) => (
          <Link
            key={link.href}
            ref={index === 0 ? firstLink : undefined}
            className="nav-link"
            href={link.href}
            aria-current={path === link.href ? 'page' : undefined}
            onClick={() => setOpen(false)}
          >
            <Icon name={link.icon} />
            {link.label}
          </Link>
        ))}
      </nav>
      <button
        type="button"
        className="scrim"
        aria-label="메뉴 닫기"
        tabIndex={-1}
        onClick={close}
      />
      <main className="main" inert={open}>
        {children}
      </main>
    </div>
  )
}
