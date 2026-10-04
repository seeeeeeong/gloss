import Link from 'next/link'
import type { ReactNode } from 'react'
import { ThemeToggle } from '@/components/ThemeToggle'

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <header className="topbar">
        <Link className="brand" href="/">
          <span className="brand-mark" aria-hidden="true">
            G
          </span>
          Gloss
        </Link>
        <div className="top-actions">
          <ThemeToggle />
        </div>
      </header>
      <main className="auth-card">{children}</main>
    </div>
  )
}
