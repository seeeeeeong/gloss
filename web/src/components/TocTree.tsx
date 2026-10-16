import type { TocNode } from '@/lib/books'

export function TocTree({ nodes }: { nodes: TocNode[] }) {
  return (
    <ol className="toc-tree">
      {nodes.map((node) => (
        <li key={node.id}>
          <span className="toc-item">
            {node.number && <span className="toc-number">{node.number}</span>}
            <span className="toc-title">{node.title}</span>
          </span>
          {node.children.length > 0 && <TocTree nodes={node.children} />}
        </li>
      ))}
    </ol>
  )
}
