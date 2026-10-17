'use client'

import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Icon } from '@/components/Icon'
import type { ApiResult } from '@/lib/api'
import { creditOf } from '@/lib/books'
import {
  contentsHeading,
  contentsIntro,
  type DocItem,
  docOf,
  factsOf,
  type HeldContents,
  itemsCount,
  keepsEditing,
} from '@/lib/setup'

/** The 수정 button of the live contents, where focus goes back when the editor closes. */
export const EDIT_BUTTON = 'contents-edit'

/** Contents Gloss brought, as its answer shows them, and what the reader may do with them. */
export type ShownContents = {
  seq: number
  held: HeldContents
  /** The contents a book would be made from now; earlier ones, and those started with, are read only. */
  live: boolean
  editing: boolean
  starting: boolean
  /** Why the last start or save did not go through, said as Gloss would. */
  notice: string | null
  onEdit: () => void
  onStart: () => void
}

type Props = { contents: ShownContents; typed: boolean }

/**
 * The contents as a document in Gloss's answer: the book's header with its facts, the source and
 * 수정, then every item, parts and chapters as headings and sections as lists, then 이 목차로 시작.
 */
export function SetupContents({ contents, typed }: Props) {
  const { held, live, editing, starting, notice, onStart } = contents
  return (
    <>
      <h1>{contentsHeading(held)}</h1>
      <BookHeader contents={contents} />
      <p>{contentsIntro(typed)}</p>
      <div className="doc">
        {docOf(held.items).map((block) => {
          if (block.kind === 'list') return <DocList key={block.key} items={block.items} />
          if (block.kind === 'part') return <h3 key={block.key}>{block.label}</h3>
          return <h4 key={block.key}>{block.label}</h4>
        })}
      </div>
      {live && (
        <div className="actions">
          {/* Not disabled while busy: a disabled button would drop focus to the page. */}
          <button
            type="button"
            className="btn primary"
            aria-disabled={starting || editing}
            onClick={() => {
              if (!starting && !editing) onStart()
            }}
          >
            {starting ? '시작하는 중' : '이 목차로 시작'}
          </button>
        </div>
      )}
      {notice && <p role="alert">{notice}</p>}
    </>
  )
}

/** The cover, the title linked to its page, the facts, then the source's credit and 수정. */
function BookHeader({ contents }: { contents: ShownContents }) {
  const { held } = contents
  const href = held.book?.url ?? held.sourceUrl
  return (
    <div className="tb">
      {held.book?.cover && (
        <div className="tb-cover">
          <Cover src={held.book.cover} />
        </div>
      )}
      <div className="tb-body">
        <p className="tb-title">
          {href ? (
            <a className="dl" href={href} target="_blank" rel="noopener noreferrer">
              {held.title}
            </a>
          ) : (
            held.title
          )}
        </p>
        <ul className="facts">
          {factsOf(held).map((fact) => (
            <li key={fact}>{fact}</li>
          ))}
        </ul>
        <SourceRow contents={contents} />
      </div>
    </div>
  )
}

function SourceRow({ contents }: { contents: ShownContents }) {
  const { held, live, editing, starting, onEdit } = contents
  const credit = held.source && held.sourceUrl ? creditOf(held.source) : null
  if (!credit && !live) return null
  return (
    <p className="bk-src">
      {credit && held.sourceUrl && (
        <a className="dl" href={held.sourceUrl} target="_blank" rel="noopener noreferrer">
          <span>{credit}</span>
          <Icon name="upRight" size={14} />
        </a>
      )}
      {live && (
        <span className="tacts">
          <button
            type="button"
            id={EDIT_BUTTON}
            className="tbtn"
            aria-expanded={editing}
            aria-disabled={starting}
            onClick={() => {
              if (!starting) onEdit()
            }}
          >
            수정
          </button>
        </span>
      )}
    </p>
  )
}

function DocList({ items }: { items: DocItem[] }) {
  return (
    <ul>
      {items.map((item) => (
        <li key={item.key}>
          {item.label}
          {item.children.length > 0 && <DocList items={item.children} />}
        </li>
      ))}
    </ul>
  )
}

type EditorProps = {
  held: HeldContents
  /** The panel is shown: only then can the field take focus. */
  open: boolean
  onSave: (toc: string) => Promise<ApiResult<unknown> | null>
  onClose: () => void
}

/** The held contents as text in the side panel, saved back to the session; the book is still not made. */
export function SetupContentsEditor({ held, open, onSave, onClose }: EditorProps) {
  const [value, setValue] = useState(held.raw)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [scrolled, setScrolled] = useState(false)
  const field = useRef<HTMLTextAreaElement>(null)
  const unchanged = value === held.raw

  useEffect(() => {
    if (open) field.current?.focus({ preventScroll: true })
  }, [open])

  // Escape closes only while nothing typed would be lost; with an edit it does nothing.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !event.isComposing && unchanged && !saving) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [unchanged, saving, onClose])

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    setSaving(true)
    setError(null)
    const result = await onSave(value)
    setSaving(false)
    if (!result) return
    if (result.ok) onClose()
    else if (keepsEditing(result)) setError(result.message)
  }

  const close = () => {
    if (!saving) onClose()
  }
  return (
    <>
      <div className={scrolled ? 'p-head scrolled' : 'p-head'}>
        <button type="button" className="ibtn back" aria-label="뒤로" onClick={close}>
          <Icon name="chevL" />
        </button>
        <h2 className="p-title" id="panel-title">
          {contentsHeading(held)}
        </h2>
        <span className="p-count">{itemsCount(held.items.length)}</span>
        <button
          type="button"
          className="ibtn close"
          aria-label="닫기"
          data-tip="닫기"
          onClick={close}
        >
          <Icon name="x" />
        </button>
      </div>
      <form
        id="toc-edit"
        className="p-body"
        onSubmit={save}
        onScroll={(event) => setScrolled(event.currentTarget.scrollTop > 0)}
      >
        <div className="p-col">
          <textarea
            ref={field}
            className="raw"
            aria-labelledby="panel-title"
            spellCheck={false}
            readOnly={saving}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            required
          />
        </div>
      </form>
      <div className="p-foot">
        <div>
          {error && (
            <p className="f-err" role="alert">
              {error}
            </p>
          )}
          <div className="actions">
            <button type="submit" form="toc-edit" className="btn primary" aria-disabled={saving}>
              {saving ? '저장 중' : '저장'}
            </button>
            <button type="button" className="btn sec" aria-disabled={saving} onClick={close}>
              취소
            </button>
          </div>
        </div>
      </div>
    </>
  )
}

// Straight from the source's server: Next's optimizer would fetch and keep a copy of every cover.
export function Cover({ src }: { src: string }) {
  // biome-ignore lint/performance/noImgElement: see above
  return <img src={src} alt="" loading="lazy" referrerPolicy="no-referrer" />
}
