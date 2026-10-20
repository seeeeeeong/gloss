'use client'

import './contents.css'
import Link from 'next/link'
import {
  type MouseEvent,
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { createPortal } from 'react-dom'
import { useShellTitle } from '@/components/layouts/ShellTitle'
import { Icon } from '@/components/ui/Icon'
import {
  ancestorsOf,
  type ContentsNode,
  contentsTreeOf,
  type ItemView,
} from '@/features/reader/utils/reader'
import { useDismiss } from '@/hooks/useDismiss'
import { editionParts, sourceNameOf, type TocItem } from '@/lib/books'
import { headingOf, readerHref } from '@/lib/posts'
import { toneOf } from '@/lib/shelf'
import { ArchiveBook } from './ArchiveBook'
import type { ReaderBook } from './ReaderPages'

export const CONTENTS_ID = 'reader-contents'
const SHUT_KEY = 'gloss.reader.contents'
const SETUP_LABEL = '이 책을 고른 대화'

type Props = {
  book: ReaderBook
  items: TocItem[]
  /** The item open; null before the book is laid out. */
  current: string | null
  phone: boolean
  viewOf: (key: string) => ItemView
  /** Items the reader keeps a bookmark in. */
  marked: ReadonlySet<string>
  /** Items the reader has talked with the tutor about. */
  talked: ReadonlySet<string>
  onGo: (key: string) => void
}

/**
 * The book's contents beside the reading: a column after the rail, folded away from its head, or a
 * drawer opened from the header on a phone. The header names the item open.
 */
export function ReaderContents(props: Props) {
  const { book, items, current, phone, viewOf, marked, talked, onGo } = props
  const shell = useShellTitle()
  const [shut, setShut] = useShut()
  const [drawer, setDrawer] = useState(false)
  const aside = useRef<HTMLDivElement>(null)
  const scroller = useRef<HTMLDivElement>(null)
  const pill = useRef<HTMLButtonElement>(null)
  const fold = useRef<HTMLButtonElement>(null)
  const reopen = useRef<HTMLButtonElement>(null)
  const closeDrawer = useCallback(() => setDrawer(false), [])
  const open = drawer && phone
  useDismiss(open, aside, pill, closeDrawer)

  const item = items.find((it) => it.itemKey === current)
  const label = item ? headingOf(item) : book.title

  // Opening the drawer takes focus to the item open; a fold hands it to the button that undoes it.
  const focusing = useRef<'row' | 'fold' | 'reopen' | null>(null)
  useEffect(() => {
    const target = focusing.current
    focusing.current = null
    if (target === 'row') rowIn(scroller.current)?.focus({ preventScroll: true })
    if (target === 'fold') fold.current?.focus()
    if (target === 'reopen') reopen.current?.focus()
  })
  // biome-ignore lint/correctness/useExhaustiveDependencies: the item open comes into view as it changes, or as the column shows
  useEffect(() => {
    const box = scroller.current
    const row = rowIn(box)
    if (!box || !row) return
    const r = row.getBoundingClientRect()
    const b = box.getBoundingClientRect()
    if (r.top < b.top || r.bottom > b.bottom) box.scrollTop += r.top - b.top - b.height / 3
  }, [current, open, shut])

  function setFolded(next: boolean) {
    focusing.current = next ? 'reopen' : 'fold'
    setShut(next)
  }
  function go(key: string) {
    if (open) {
      setDrawer(false)
      pill.current?.focus({ preventScroll: true })
    }
    onGo(key)
  }

  const className = ['tree-col', shut && 'shut', open && 'open'].filter(Boolean).join(' ')
  return (
    <>
      {shell.bar &&
        createPortal(
          <>
            {shut && (
              <button
                ref={reopen}
                type="button"
                className="ibtn tert tree-reopen"
                aria-label="차례 열기"
                aria-controls={CONTENTS_ID}
                aria-expanded={false}
                data-tip="차례 열기"
                onClick={() => setFolded(false)}
              >
                <Icon name="sidebar" />
              </button>
            )}
            <p className="top-here">{label}</p>
          </>,
          shell.bar,
        )}
      {shell.pill &&
        createPortal(
          <button
            ref={pill}
            type="button"
            className="name"
            aria-expanded={open}
            aria-controls={CONTENTS_ID}
            onClick={() => {
              focusing.current = 'row'
              setDrawer(true)
            }}
          >
            <span>{label}</span>
            <Icon name="chevD" size={16} />
          </button>,
          shell.pill,
        )}
      {onPhone(
        phone,
        <>
          {open && (
            <button
              type="button"
              className="tree-scrim"
              aria-label="차례 닫기"
              tabIndex={-1}
              onClick={closeDrawer}
            />
          )}
          <div ref={aside} id={CONTENTS_ID} className={className}>
            <div className="tree-head">
              <span className={`spine tone-${toneOf(book.id)}`} aria-hidden="true" />
              <BookTitle book={book} />
              {book.setupHref && (
                <Link
                  className="ibtn sm tert"
                  href={book.setupHref}
                  aria-label={SETUP_LABEL}
                  data-tip={SETUP_LABEL}
                >
                  <Icon name="chat" />
                </Link>
              )}
              <ArchiveBook bookId={book.id} />
              {phone ? (
                <button
                  type="button"
                  className="ibtn sm tert"
                  aria-label="차례 닫기"
                  onClick={() => {
                    setDrawer(false)
                    pill.current?.focus({ preventScroll: true })
                  }}
                >
                  <Icon name="x" />
                </button>
              ) : (
                <button
                  ref={fold}
                  type="button"
                  className="ibtn sm tert"
                  aria-label="차례 닫기"
                  aria-controls={CONTENTS_ID}
                  aria-expanded={true}
                  data-tip="차례 닫기"
                  onClick={() => setFolded(true)}
                >
                  <Icon name="sidebar" />
                </button>
              )}
            </div>
            <div ref={scroller} className="tree-scroll">
              <ContentsTree
                bookId={book.id}
                items={items}
                current={current}
                viewOf={viewOf}
                marked={marked}
                talked={talked}
                onGo={go}
              />
            </div>
          </div>
        </>,
      )}
    </>
  )
}

// On a phone the drawer leaves the main area, whose container query would trap it under the tutor's button.
const onPhone = (phone: boolean, node: ReactNode) =>
  phone ? createPortal(node, document.body) : node

const rowIn = (box: HTMLElement | null) =>
  box?.querySelector<HTMLElement>('[aria-current="page"]') ?? null

/** Whether the column is folded away on a wide screen, kept in this browser. */
function useShut(): [boolean, (shut: boolean) => void] {
  const [shut, setShut] = useState(() => {
    try {
      return localStorage.getItem(SHUT_KEY) === 'shut'
    } catch {
      return false
    }
  })
  const choose = useCallback((next: boolean) => {
    setShut(next)
    try {
      localStorage.setItem(SHUT_KEY, next ? 'shut' : 'open')
    } catch {}
  }, [])
  return [shut, choose]
}

type TreeProps = Omit<Props, 'book' | 'phone'> & { bookId: string }

/**
 * Every item as a link: parts over chapters over sections, a chapter's sections folded unless the
 * reader opens it or reads in it. An item not yet written is quieter; the reader's bookmarks and
 * talks with the tutor are marked in vermilion.
 */
export function ContentsTree(props: TreeProps) {
  const { bookId, items, current, viewOf, marked, talked, onGo } = props
  const tree = useMemo(() => contentsTreeOf(items), [items])
  const [unfolded, setUnfolded] = useState<ReadonlySet<string>>(() =>
    withPathTo(new Set(), items, current),
  )
  // Reading into another chapter opens it, and leaves open what the reader opened.
  const [seen, setSeen] = useState(current)
  if (current !== seen) {
    setSeen(current)
    setUnfolded(withPathTo(unfolded, items, current))
  }
  const written = items.filter((it) => viewOf(it.itemKey).kind === 'written').length

  function toggle(key: string) {
    const next = new Set(unfolded)
    if (!next.delete(key)) next.add(key)
    setUnfolded(next)
  }
  const follow = (key: string) => (event: MouseEvent) => {
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
    event.preventDefault()
    onGo(key)
  }
  // A top item opens a group in the weight of a heading; the level under it is plain, the next quieter.
  const row = (node: ContentsNode, level: number) => {
    const { itemKey, number, title } = node.item
    const view = viewOf(itemKey)
    const cls = ['tr-row', `d${Math.min(level, 3)}`, view.kind !== 'written' && 'todo'].filter(
      Boolean,
    )
    return (
      <a
        className={cls.join(' ')}
        href={readerHref(bookId, itemKey)}
        aria-current={itemKey === current ? 'page' : undefined}
        onClick={follow(itemKey)}
      >
        {number && <span className="no">{number}</span>}
        <span className="t">{title}</span>
        <RowMarks
          live={view.kind === 'writing'}
          talked={talked.has(itemKey)}
          marked={marked.has(itemKey)}
        />
      </a>
    )
  }
  const sections = (nodes: ContentsNode[], level: number): ReactNode =>
    nodes.length > 0 && (
      <ul className="tr-secs">
        {nodes.map((node) => (
          <li key={node.item.itemKey}>
            {row(node, level)}
            {sections(node.children, level + 1)}
          </li>
        ))}
      </ul>
    )
  const chapter = (node: ContentsNode, level: number) => {
    const key = node.item.itemKey
    const open = unfolded.has(key)
    const folds = node.children.length > 0
    return (
      <li key={key} className={open ? 'tr-ch open' : 'tr-ch'}>
        <div className="tr-line">
          {folds && (
            <button
              type="button"
              className="tr-fold"
              aria-expanded={open}
              aria-label={`${headingOf(node.item)} ${open ? '접기' : '펼치기'}`}
              onClick={() => toggle(key)}
            >
              <Icon name="chevR" size={14} />
            </button>
          )}
          {row(node, level)}
        </div>
        {folds && open && sections(node.children, level + 1)}
      </li>
    )
  }
  return (
    <nav className="tree" aria-label="차례">
      <p className="tree-cap">
        <span>차례</span>
        <span>
          {written} / {items.length}
        </span>
      </p>
      <ul>
        {tree.map((node) =>
          node.kind === 'part' ? (
            <li key={node.item.itemKey} className="tr-part">
              {row(node, 1)}
              <ul>{node.children.map((child) => chapter(child, 2))}</ul>
            </li>
          ) : (
            chapter(node, 1)
          ),
        )}
      </ul>
    </nav>
  )
}

/** [open] with every item above [key] opened, so the item shows. */
function withPathTo(
  open: ReadonlySet<string>,
  items: TocItem[],
  key: string | null,
): ReadonlySet<string> {
  const item = items.find((it) => it.itemKey === key)
  if (!item) return open
  const path = ancestorsOf(items, item).map((up) => up.itemKey)
  if (path.every((up) => open.has(up))) return open
  return new Set([...open, ...path])
}

function RowMarks({ live, talked, marked }: { live: boolean; talked: boolean; marked: boolean }) {
  if (!live && !talked && !marked) return null
  return (
    <span className="st">
      {live && <span className="spin" role="img" aria-label="쓰는 중" />}
      {talked && (
        <span className="ic-chat" role="img" aria-label="튜터와 나눈 대화">
          <Icon name="chat" size={15} />
        </span>
      )}
      {marked && (
        <span className="ic-mark" role="img" aria-label="책갈피">
          <Icon name="bookmark" size={14} />
        </span>
      )}
    </span>
  )
}

/** The title, and under it what tells this edition from another, its source named and linked. */
function BookTitle({ book }: { book: ReaderBook }) {
  const edition = editionParts({
    publisher: book.publisher ?? null,
    publishedOn: book.publishedOn ?? null,
    kind: book.kind ?? null,
  }).join(' · ')
  // The source's terms ask for its name and a link wherever its details show.
  const source = book.source ? sourceNameOf(book.source) : null
  const credit = source && book.sourceUrl ? { name: source, url: book.sourceUrl } : null
  return (
    <div className="bt">
      <span className="bt-title">{book.title}</span>
      {(edition || credit) && (
        <span className="bt-edition">
          {edition && <span>{edition}</span>}
          {credit && (
            <span className="bt-source">
              {edition && ' · '}
              <a className="dl" href={credit.url} target="_blank" rel="noopener noreferrer">
                {credit.name}
              </a>
            </span>
          )}
        </span>
      )}
    </div>
  )
}
