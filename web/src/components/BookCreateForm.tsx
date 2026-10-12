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
export function BookCreateForm(props: { found?: BookCandidate | null; onCancel?: () => void }) {
  const { found, onCancel } = props
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const contents = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (found) contents.current?.focus()
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
    // Stays pending: the form is done once the book exists, and the next page is on its way.
    router.push(`/books/${result.data.id}`)
  }

  return (
    <form className="form" onSubmit={create}>
      {found ? (
        <p className="found">
          <span className="shelf-title">{found.title}</span>
          <span className="shelf-meta">{editionOf(found)}</span>
        </p>
      ) : (
        <>
          <label className="field">
            <span className="field-label">제목</span>
            <input className="input" name="title" autoComplete="off" disabled={pending} required />
          </label>
          <label className="field">
            <span className="field-label">저자</span>
            <input
              className="input"
              name="authors"
              autoComplete="off"
              placeholder="쉼표로 구분"
              disabled={pending}
            />
          </label>
          <label className="field">
            <span className="field-label">출판사</span>
            <input className="input" name="publisher" autoComplete="off" disabled={pending} />
          </label>
          <label className="field">
            <span className="field-label">ISBN</span>
            <input
              className="input"
              name="isbn13"
              inputMode="numeric"
              autoComplete="off"
              spellCheck={false}
              disabled={pending}
            />
          </label>
        </>
      )}
      <label className="field">
        <span className="field-label">목차</span>
        <textarea
          ref={contents}
          className="input textarea"
          name="toc"
          spellCheck={false}
          placeholder={TOC_EXAMPLE}
          disabled={pending}
          required
        />
      </label>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="form-actions">
        {found && onCancel && (
          <button type="button" className="btn btn-ghost" disabled={pending} onClick={onCancel}>
            취소
          </button>
        )}
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? '추가 중…' : '추가하고 목차 확인'}
        </button>
      </div>
    </form>
  )
}
