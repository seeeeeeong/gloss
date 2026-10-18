'use client'

import Link from 'next/link'
import { useState } from 'react'
import { Dialog } from '@/components/ui/Dialog'
import { Icon } from '@/components/ui/Icon'
import { matching, toneOf } from '@/lib/shelf'

type Props = { books: { id: string; title: string }[]; onClose: () => void }

/** 서재 검색: the books whose title holds what is typed, each a way into it. */
export function ShelfSearch({ books, onClose }: Props) {
  const [query, setQuery] = useState('')
  const found = matching(books, query)
  return (
    <Dialog labelledBy="search-title" onClose={onClose} look="search">
      <h2 id="search-title" className="sr-only">
        서재 검색
      </h2>
      <label className="search-in">
        <Icon name="search" />
        <input
          type="search"
          aria-label="전체 책 검색"
          data-autofocus
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </label>
      {found.length > 0 ? (
        <ul className="search-hits">
          {found.map((book) => (
            <li key={book.id}>
              <Link className="hit" href={`/books/${book.id}`} onClick={onClose}>
                <span className={`spine tone-${toneOf(book.id)}`} aria-hidden="true" />
                <b>{book.title}</b>
                <Icon name="arrowR" size={18} />
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <div className="search-none">
          <p>검색 결과가 없습니다.</p>
          <Link className="tbtn" href="/books/new" onClick={onClose}>
            책 추가
          </Link>
        </div>
      )}
      <div className="search-foot">
        <span>{found.length}권의 책</span>
        <span>
          <kbd>esc</kbd> 닫기
        </span>
      </div>
    </Dialog>
  )
}
