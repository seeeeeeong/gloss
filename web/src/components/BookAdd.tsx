'use client'

import { useState } from 'react'
import { BookCreateForm } from '@/components/BookCreateForm'
import { BookSearch } from '@/components/BookSearch'
import type { BookCandidate } from '@/lib/books'

/** A book is found by its title, or typed in; for an edition found without contents, only those are typed. */
export function BookAdd() {
  const [typed, setTyped] = useState<BookCandidate | null>(null)
  return (
    <>
      <BookSearch onType={setTyped} />
      <section className="section book-typed" aria-labelledby="typed">
        <h2 className="section-title" id="typed">
          {typed ? '목차 입력' : '직접 입력'}
        </h2>
        {/* A new key starts the form over for the edition picked. */}
        <BookCreateForm key={typed?.ref ?? 'blank'} found={typed} onCancel={() => setTyped(null)} />
      </section>
    </>
  )
}
