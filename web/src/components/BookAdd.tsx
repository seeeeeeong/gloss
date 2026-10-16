'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { BookCreateForm } from '@/components/BookCreateForm'
import { BookSearch } from '@/components/BookSearch'
import { Composer } from '@/components/Composer'
import { load, send } from '@/lib/api'
import { type Book, type BookCandidate, searchPath } from '@/lib/books'

// The server's limit for a search.
const MAX_QUERY = 100

/**
 * A book is found by its title, or typed in; for an edition found without contents, only those are
 * typed. Empty, the screen is a heading over the composer; a search or the form docks it below.
 */
export function BookAdd() {
  const router = useRouter()
  const [query, setQuery] = useState<string | null>(null)
  const [found, setFound] = useState<BookCandidate[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)
  const [adding, setAdding] = useState<string | null>(null)
  const [typing, setTyping] = useState<BookCandidate | 'blank' | null>(null)
  const busy = searching || adding !== null

  async function search(text: string) {
    setQuery(text)
    setSearching(true)
    setError(null)
    // Gone at once: editions left under a failed search would pass for its results.
    setFound(null)
    const result = await load<BookCandidate[]>(searchPath(text))
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
    // Stays busy: the book exists, and its page is on its way; the sidebar lists it too.
    router.push(`/books/${result.data.id}`)
    router.refresh()
  }

  const composer = (docked: boolean) => (
    <Composer
      label="책 제목"
      placeholder="책 제목"
      maxLength={MAX_QUERY}
      busy={busy}
      autoFocus={docked}
      onSend={search}
      plus={{
        label: '직접 입력',
        expanded: typing === 'blank',
        onClick: () => setTyping(typing === 'blank' ? null : 'blank'),
      }}
    />
  )

  if (query === null && typing === null) {
    return (
      <div className="home">
        <div className="wrap">
          <div className="col">
            <h1>어떤 도서를 학습할까요?</h1>
            {composer(false)}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="scroll">
      <div className="wrap">
        <div className="col thread">
          {query !== null && (
            <>
              <div className="turn user">
                <div className="bubble">{query}</div>
              </div>
              <div className="turn gloss">
                <BookSearch
                  found={found}
                  error={error}
                  searching={searching}
                  adding={adding}
                  onAdd={add}
                  onType={setTyping}
                />
              </div>
            </>
          )}
          {typing !== null && (
            <section className="turn gloss" aria-labelledby="typed">
              <h2 className="sub-h" id="typed">
                {typing === 'blank' ? '직접 입력' : '목차 입력'}
              </h2>
              {/* A new key starts the form over for the edition picked. */}
              <BookCreateForm
                key={typing === 'blank' ? 'blank' : typing.ref}
                found={typing === 'blank' ? null : typing}
                onCancel={() => setTyping(null)}
              />
            </section>
          )}
        </div>
      </div>
      <div className="dock">
        <div className="wrap">
          <div className="col">{composer(true)}</div>
        </div>
      </div>
    </div>
  )
}
