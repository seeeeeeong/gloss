import type { ReactNode } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * A post's Markdown, which a model wrote, so it is not trusted: raw HTML stays text and a link
 * keeps only a safe scheme, as the library does by default.
 */
export function Prose({ markdown }: { markdown: string }) {
  return (
    <div className="prose">
      <Markdown
        remarkPlugins={[remarkGfm]}
        // An image would call a host the model chose, on the reader's behalf.
        disallowedElements={['img']}
        components={{ a: OutsideLink, table: WideTable }}
      >
        {markdown}
      </Markdown>
    </div>
  )
}

function OutsideLink({ href, children }: { href?: string; children?: ReactNode }) {
  return (
    <a href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  )
}

function WideTable({ children }: { children?: ReactNode }) {
  return (
    <div className="table-wrap">
      <table>{children}</table>
    </div>
  )
}
