import type { ReactNode } from 'react'

export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <header className="a-top">
        <span className="wordmark">Gloss</span>
      </header>
      <main className="auth">
        <div className="acol">{children}</div>
      </main>
    </>
  )
}
