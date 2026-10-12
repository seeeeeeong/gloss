import Link from 'next/link'
import type { TocNode } from '@/lib/books'
import { postHref } from '@/lib/posts'

/** With [reading], each section links to its post and shows whether one is written. */
type Props = {
  nodes: TocNode[]
  reading?: { bookId: string; written: ReadonlySet<string> }
}

export function TocTree({ nodes, reading }: Props) {
  return (
    <ol className="toc-tree">
      {nodes.map((node) => (
        <li key={node.id}>
          {reading ? (
            <Link className="toc-item toc-link" href={postHref(reading.bookId, node.itemKey)}>
              <Section node={node} />
              <span className="toc-mark" data-written={reading.written.has(node.itemKey)}>
                <span className="sr-only">
                  {reading.written.has(node.itemKey) ? '글 있음' : '글 없음'}
                </span>
              </span>
            </Link>
          ) : (
            <span className="toc-item">
              <Section node={node} />
            </span>
          )}
          {node.children.length > 0 && <TocTree nodes={node.children} reading={reading} />}
        </li>
      ))}
    </ol>
  )
}

function Section({ node }: { node: TocNode }) {
  return (
    <>
      {node.number && <span className="toc-number">{node.number}</span>}
      <span className="toc-title">{node.title}</span>
    </>
  )
}
