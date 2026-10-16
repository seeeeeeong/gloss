'use client'

import { type FormEvent, useEffect, useRef, useState } from 'react'
import { Icon } from '@/components/Icon'
import { SourceCredit } from '@/components/SourceCredit'
import { TocTree } from '@/components/TocTree'
import type { ApiResult } from '@/lib/api'
import { bylineOf } from '@/lib/books'
import { type HeldContents, heldTree, keepsEditing } from '@/lib/setup'

type Props = {
  held: HeldContents
  onEdit: (toc: string) => Promise<ApiResult<unknown> | null>
  onStart: () => Promise<ApiResult<unknown> | null>
  onClose: () => void
}

/**
 * The picked edition's contents in the side panel, held in the session: read as a tree, edited as
 * text, and made into the book only when the reader starts with them.
 */
export function SetupContents({ held, onEdit, onStart, onClose }: Props) {
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  const [pending, setPending] = useState<'start' | 'save' | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    heading.current?.focus({ preventScroll: true })
  }, [])

  // Not while editing, where Escape would throw the edit away.
  useEffect(() => {
    if (editing) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [editing, onClose])

  async function start() {
    setPending('start')
    setError(null)
    const result = await onStart()
    // Stays busy once started: the book's page is on its way.
    if (result?.ok) return
    setPending(null)
    if (result) setError(result.message)
  }

  function edit() {
    setValue(held.raw)
    setError(null)
    setEditing(true)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending('save')
    setError(null)
    const result = await onEdit(value)
    setPending(null)
    if (!result) return
    if (!result.ok) setError(result.message)
    setEditing(keepsEditing(result))
  }

  const byline = held.book ? bylineOf(held.book) : held.authors.join(', ')
  return (
    <>
      <div className="p-head">
        <button type="button" className="ibtn back" aria-label="뒤로" onClick={onClose}>
          <Icon name="chevL" />
        </button>
        <h2 className="p-title" id="panel-title" ref={heading} tabIndex={-1}>
          {held.title}
        </h2>
        <span className="p-count">항목 {held.items.length}개</span>
        <button
          type="button"
          className="ibtn close"
          aria-label="닫기"
          data-tip="닫기"
          onClick={onClose}
        >
          <Icon name="x" />
        </button>
      </div>
      <form id="toc-edit" className="p-body" onSubmit={save}>
        <div className="p-col">
          {editing ? (
            <textarea
              className="raw"
              aria-label="목차 수정"
              spellCheck={false}
              disabled={pending !== null}
              value={value}
              onChange={(event) => setValue(event.target.value)}
              required
            />
          ) : (
            <>
              <p className="p-meta">
                {byline}
                {byline && held.source && ' · '}
                {held.source && (
                  <SourceCredit book={{ source: held.source, sourceUrl: held.sourceUrl }} />
                )}
              </p>
              <div className="toc">
                <TocTree nodes={heldTree(held.items)} />
              </div>
            </>
          )}
        </div>
      </form>
      <Foot
        editing={editing}
        pending={pending}
        error={error}
        onStart={start}
        onEdit={edit}
        onCancel={() => setEditing(false)}
      />
    </>
  )
}

type FootProps = {
  editing: boolean
  pending: 'start' | 'save' | null
  error: string | null
  onStart: () => void
  onEdit: () => void
  onCancel: () => void
}

/** 이 목차로 시작 and 수정 under the tree; 저장 and 취소 under the editor, which submits the panel's form. */
function Foot({ editing, pending, error, onStart, onEdit, onCancel }: FootProps) {
  const busy = pending !== null
  return (
    <div className="p-foot">
      <div>
        {error && (
          <p className="f-err" role="alert">
            {error}
          </p>
        )}
        {editing ? (
          <div className="actions">
            <button type="submit" form="toc-edit" className="btn primary" disabled={busy}>
              {pending === 'save' ? '저장 중' : '저장'}
            </button>
            <button type="button" className="btn sec" disabled={busy} onClick={onCancel}>
              취소
            </button>
          </div>
        ) : (
          <div className="actions">
            <button type="button" className="btn primary" disabled={busy} onClick={onStart}>
              {pending === 'start' ? '시작하는 중' : '이 목차로 시작'}
            </button>
            <button type="button" className="btn sec" disabled={busy} onClick={onEdit}>
              수정
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
