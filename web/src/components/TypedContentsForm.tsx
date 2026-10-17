'use client'

import { type FormEvent, useEffect, useRef, useState } from 'react'
import type { ApiResult } from '@/lib/api'
import { authorsOf, editionOf } from '@/lib/books'
import type { OfferedBook, TypedContents } from '@/lib/setup'

type Props = {
  /** An edition this session found without contents; null for a book typed in whole. */
  found: OfferedBook | null
  onSubmit: (typed: TypedContents) => Promise<ApiResult<unknown> | null>
  onCancel: () => void
}

/**
 * Contents the reader types in, held in the session as a pick's are: for `found`, or for a book of
 * their own. No book is made here; the thread shows them as a pick's.
 */
export function TypedContentsForm({ found, onSubmit, onCancel }: Props) {
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const first = useRef<HTMLInputElement>(null)
  const contents = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (found) contents.current?.focus()
    else first.current?.focus()
  }, [found])

  async function hold(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const toc = String(form.get('toc') ?? '')
    setPending(true)
    setError(null)
    const result = await onSubmit(
      found
        ? { ref: found.ref, toc }
        : {
            title: String(form.get('title') ?? ''),
            authors: authorsOf(String(form.get('authors') ?? '')),
            toc,
          },
    )
    setPending(false)
    if (result && !result.ok) setError(result.message)
  }

  return (
    <form className="s-form" onSubmit={hold}>
      {found ? (
        <div>
          <p className="bk-title">{found.title}</p>
          <p className="bk-meta">{editionOf(found)}</p>
        </div>
      ) : (
        <>
          <label className="f-l">
            제목
            <input
              ref={first}
              className="field"
              name="title"
              autoComplete="off"
              disabled={pending}
              required
            />
          </label>
          <label className="f-l">
            저자
            <input className="field" name="authors" autoComplete="off" disabled={pending} />
          </label>
        </>
      )}
      <label className="f-l">
        목차
        <textarea
          ref={contents}
          className="raw"
          name="toc"
          spellCheck={false}
          disabled={pending}
          required
        />
      </label>
      {error && (
        <p className="f-err" role="alert">
          {error}
        </p>
      )}
      <div className="actions">
        <button type="submit" className="btn primary" disabled={pending}>
          {pending ? '보내는 중' : '목차 보기'}
        </button>
        <button type="button" className="btn sec" disabled={pending} onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  )
}
