'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { send } from '@/lib/api'
import { authorsOf, type Book, type BookCandidate } from '@/lib/books'

// Shows the shape the server reads: one item a line, depth from numbering or indent.
const TOC_EXAMPLE = '1장 시작하기\n  1.1 설치\n  1.2 첫 프로그램\n2장 자료형'

const optional = (value: FormDataEntryValue | null) => String(value ?? '').trim() || null

/** `found` is an edition the source has no contents for: its details are filled in, the contents typed. */
export function BookCreateForm({ found }: { found?: BookCandidate | null }) {
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
    const result = await send<Book>('POST', '/api/me/books', {
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
      <label className="field">
        <span className="field-label">제목</span>
        <input
          className="input"
          name="title"
          autoComplete="off"
          defaultValue={found?.title}
          disabled={pending}
          required
        />
      </label>
      <label className="field">
        <span className="field-label">저자</span>
        <input
          className="input"
          name="authors"
          autoComplete="off"
          placeholder="쉼표로 구분"
          defaultValue={found?.authors.join(', ')}
          disabled={pending}
        />
      </label>
      <label className="field">
        <span className="field-label">출판사</span>
        <input
          className="input"
          name="publisher"
          autoComplete="off"
          defaultValue={found?.publisher ?? undefined}
          disabled={pending}
        />
      </label>
      <label className="field">
        <span className="field-label">ISBN</span>
        <input
          className="input"
          name="isbn13"
          inputMode="numeric"
          autoComplete="off"
          spellCheck={false}
          defaultValue={found?.isbn13 ?? undefined}
          disabled={pending}
        />
      </label>
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
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? '추가 중…' : '추가하고 목차 확인'}
        </button>
      </div>
    </form>
  )
}
