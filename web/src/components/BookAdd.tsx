'use client'

import { useState } from 'react'
import { BookCreateForm } from '@/components/BookCreateForm'
import { BookSearch } from '@/components/BookSearch'
import type { BookCandidate } from '@/lib/books'

/** A book is found by its title, or typed in; an edition found without contents fills the form. */
export function BookAdd() {
  const [typed, setTyped] = useState<BookCandidate | null>(null)
  return (
    <>
      <BookSearch onType={setTyped} />
      <section className="section book-typed" aria-labelledby="typed">
        <h2 className="section-title" id="typed">
          직접 입력
        </h2>
        {/* A new key starts the form over with the edition's details. */}
        <BookCreateForm key={typed?.ref ?? 'blank'} found={typed} />
      </section>
    </>
  )
}
