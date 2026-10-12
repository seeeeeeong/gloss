'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { load, send } from '@/lib/api'
import { type Book, type BookCandidate, creditOf, editionOf, searchPath } from '@/lib/books'

/** `onType` takes an edition the source has no contents for, to be typed in below. */
export function BookSearch({ onType }: { onType: (candidate: BookCandidate) => void }) {
  const router = useRouter()
  const [found, setFound] = useState<BookCandidate[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)
  const [adding, setAdding] = useState<string | null>(null)
  const busy = searching || adding !== null

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const query = String(new FormData(event.currentTarget).get('query') ?? '').trim()
    if (!query) return
    setSearching(true)
    setError(null)
    const result = await load<BookCandidate[]>(searchPath(query))
    setSearching(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    setFound(result.data)
  }

  async function add(candidate: BookCandidate) {
    setAdding(candidate.ref)
    setError(null)
    const result = await send<Book>('POST', '/api/me/books/from-source', { ref: candidate.ref })
    if (!result.ok) {
      setAdding(null)
      setError(result.message)
      return
    }
    // Stays busy: the book exists, and its page is on its way.
    router.push(`/books/${result.data.id}`)
  }

  const credit = found?.[0] ? creditOf(found[0].source) : null
  return (
    <div className="form">
      <search>
        <form className="search" onSubmit={search}>
          <input
            className="input"
            name="query"
            type="search"
            aria-label="책 제목"
            placeholder="책 제목"
            autoComplete="off"
            maxLength={100}
            disabled={busy}
            required
          />
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {searching ? '검색 중…' : '검색'}
          </button>
        </form>
      </search>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {found?.length === 0 && <div className="empty">검색 결과가 없습니다.</div>}
      {found && found.length > 0 && (
        <>
          <ul className="shelf">
            {found.map((candidate) => (
              <li className="candidate" key={candidate.ref}>
                <div className="candidate-text">
                  <a
                    className="shelf-title"
                    href={candidate.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {candidate.title}
                  </a>
                  <span className="shelf-meta">
                    {editionOf(candidate)}
                    {!candidate.hasContents && <span className="tag">목차 없음</span>}
                  </span>
                </div>
                {candidate.hasContents ? (
                  <button
                    type="button"
                    className="btn btn-sm btn-primary"
                    disabled={busy}
                    onClick={() => add(candidate)}
                  >
                    {adding === candidate.ref ? '추가 중…' : '추가'}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="btn btn-sm btn-ghost"
                    disabled={busy}
                    onClick={() => onType(candidate)}
                  >
                    직접 입력
                  </button>
                )}
              </li>
            ))}
          </ul>
          {credit && <p className="field-hint credit">{credit}</p>}
        </>
      )}
    </div>
  )
}
