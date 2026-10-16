import type { ReactNode } from 'react'

/** A page inside the shell: its own scroll, and the thread's column in it. */
export function Page({ children }: { children: ReactNode }) {
  return (
    <div className="scroll">
      <div className="wrap">
        <div className="col page">{children}</div>
      </div>
    </div>
  )
}
