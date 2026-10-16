'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { send } from '@/lib/api'
import { authorsOf, type Book, type BookCandidate, editionOf } from '@/lib/books'

// Shows the shape the server reads: one item a line, depth from numbering or indent.
const TOC_EXAMPLE = '1장 시작하기\n  1.1 설치\n  1.2 첫 프로그램\n2장 자료형'

const optional = (value: FormDataEntryValue | null) => String(value ?? '').trim() || null

/**
 * A book typed in whole, or only the contents of `found`: an edition the source knows but has no
 * contents for. Its details stay the source's, so the book still names where they came from.
 */
export function BookCreateForm(props: { found: BookCandidate | null; onCancel: () => void }) {
  const { found, onCancel } = props
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const first = useRef<HTMLInputElement>(null)
  const contents = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (found) contents.current?.focus()
    else first.current?.focus()
  }, [found])

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setPending(true)
    setError(null)
    const result = found
      ? await send<Book>('POST', '/api/me/books/from-source', {
          ref: found.ref,
          toc: form.get('toc'),
        })
      : await send<Book>('POST', '/api/me/books', {
          title: form.get('title'),
          authors: authorsOf(String(form.get('authors') ?? '')),
          publisher: optional(form.get('publisher')),
          isbn13: optional(form.get('isbn13')),
          toc: form.get('toc'),
        })
    if (!result.ok) {
      setPending(false)
      setError(result.message)
      return
    }
    // Stays pending: the book exists, and its page is on its way; the sidebar lists it too.
    router.push(`/books/${result.data.id}`)
    router.refresh()
  }

  return (
    <form className="s-form" onSubmit={create}>
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
            <input
              className="field"
              name="authors"
              autoComplete="off"
              placeholder="쉼표로 구분"
              disabled={pending}
            />
          </label>
          <label className="f-l">
            출판사
            <input className="field" name="publisher" autoComplete="off" disabled={pending} />
          </label>
          <label className="f-l">
            ISBN
            <input
              className="field"
              name="isbn13"
              inputMode="numeric"
              autoComplete="off"
              spellCheck={false}
              disabled={pending}
            />
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
          placeholder={TOC_EXAMPLE}
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
          {pending ? '추가 중' : '추가하고 목차 확인'}
        </button>
        <button type="button" className="btn sec" disabled={pending} onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  )
}
