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
        rehypePlugins={[keepKeyTerms]}
        // An image would call a host the model chose, on the reader's behalf.
        disallowedElements={['img']}
        components={{ a: OutsideLink, table: WideTable }}
      >
        {markdown}
      </Markdown>
    </div>
  )
}

/** The part of a hast tree this plugin reads; the rest passes through untouched. */
type HastNode = {
  type: string
  tagName?: string
  value?: string
  properties?: Record<string, unknown>
  children?: HastNode[]
}

const KEY_TERMS = '핵심 용어'
const textOf = (node: HastNode): string =>
  node.type === 'text' ? (node.value ?? '') : (node.children ?? []).map(textOf).join('')

/**
 * A lesson closes on its key terms, one list a page turn would split: from the top-level
 * "## 핵심 용어" on, the post is one section that stays on one page. Parsed, so a fenced line
 * reading the same is code, not a heading.
 */
export function keepKeyTerms() {
  return (tree: HastNode) => {
    const children = tree.children ?? []
    const at = children.findIndex(
      (node) => node.tagName === 'h2' && textOf(node).trim() === KEY_TERMS,
    )
    if (at < 0) return
    const keep: HastNode = {
      type: 'element',
      tagName: 'section',
      properties: { className: ['keep'] },
      children: children.slice(at),
    }
    tree.children = [...children.slice(0, at), keep]
  }
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
