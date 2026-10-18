import type { MouseEvent, RefObject } from 'react'
import { Icon } from '@/components/ui/Icon'
import { ancestorsOf, type ItemView } from '@/features/reader/utils/reader'
import { lineOf, type ReadingSize } from '@/features/reader/utils/reading'
import type { TocItem } from '@/lib/books'
import { headingOf, readerHref } from '@/lib/posts'
import { ItemArticle } from './ReaderPages'

type Props = {
  book: { id: string; title: string }
  items: TocItem[]
  /** The index of the item shown. */
  at: number
  viewOf: (key: string) => ItemView
  markdown: string | null
  /** The post's text, where a phrase picked can be asked about. */
  text: RefObject<HTMLDivElement | null>
  size: ReadingSize
  /** An item in the path, gone to by name. */
  onGo: (key: string) => void
  /** The item before or after, as a page turn is. */
  onTurn: (key: string) => void
  onRetry: (key: string) => void
}

/** One item as a page of its own: where it sits in the book, its post, and the items either side. */
export function ReaderPost(props: Props) {
  const { book, items, at, viewOf, markdown, text, size, onGo, onTurn, onRetry } = props
  const item = items[at]
  if (!item) return null
  const follow = (key: string, then: (key: string) => void) => (event: MouseEvent) => {
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
    event.preventDefault()
    then(key)
  }
  const prev = items[at - 1]
  const next = items[at + 1]
  return (
    <div className="post-col">
      <nav className="post-path" aria-label="위치">
        <span>{book.title}</span>
        {ancestorsOf(items, item).map((up) => (
          <span key={up.itemKey}>
            <span aria-hidden="true">/</span>
            <a href={readerHref(book.id, up.itemKey)} onClick={follow(up.itemKey, onGo)}>
              {headingOf(up)}
            </a>
          </span>
        ))}
      </nav>
      <div
        ref={text}
        className="post-text"
        style={{ fontSize: size, lineHeight: `${lineOf(size)}px` }}
      >
        <ItemArticle
          unit={at}
          number={item.number}
          title={item.title}
          view={viewOf(item.itemKey)}
          markdown={markdown}
          onRetry={() => onRetry(item.itemKey)}
        />
      </div>
      <nav className="post-nav" aria-label="이전 글과 다음 글">
        {prev ? (
          <a
            className="nav-card prev"
            href={readerHref(book.id, prev.itemKey)}
            onClick={follow(prev.itemKey, onTurn)}
          >
            <span className="k">
              <Icon name="arrowL" size={14} />
              이전
            </span>
            <NavTitle item={prev} live={viewOf(prev.itemKey).kind === 'writing'} />
          </a>
        ) : (
          <span />
        )}
        {next && (
          <a
            className="nav-card next"
            href={readerHref(book.id, next.itemKey)}
            onClick={follow(next.itemKey, onTurn)}
          >
            <span className="k">
              <Icon name="arrowR" size={14} />
              다음
            </span>
            <NavTitle item={next} live={viewOf(next.itemKey).kind === 'writing'} />
          </a>
        )}
      </nav>
    </div>
  )
}

function NavTitle({ item, live }: { item: TocItem; live: boolean }) {
  return (
    <span className="v">
      {/* The space keeps the number apart from the title in copied text and read aloud. */}
      {item.number && <span className="no">{item.number} </span>}
      <span>{item.title}</span>
      {live && <span className="spin" role="img" aria-label="쓰는 중" />}
    </span>
  )
}
