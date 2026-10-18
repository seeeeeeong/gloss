'use client'

import './library.css'
import Link from 'next/link'
import { useEffect, useState } from 'react'
import { authorsLine, type BookSummary } from '@/lib/books'
import { readerHref } from '@/lib/posts'
import {
  matching,
  monogramOf,
  placeOf,
  RECENT_KEY,
  type RecentRead,
  readsFrom,
  toneOf,
} from '@/lib/shelf'
import { Icon } from './Icon'

type Filter = 'all' | 'recent'
type Look = 'grid' | 'list'

const LOOK_KEY = 'gloss.shelf.look'

/**
 * 내 서재: the item last read to go back to, then every book, or the ones read lately, as covers or
 * as a list, narrowed by a search.
 */
export function Library({ books }: { books: BookSummary[] }) {
  const [reads, setReads] = useState<RecentRead[]>([])
  const [filter, setFilter] = useState<Filter>('all')
  const [look, setLook] = useState<Look>('grid')
  const [query, setQuery] = useState('')

  // What the reader read and how they like the shelf live in this browser, so they arrive after it renders.
  useEffect(() => {
    try {
      setReads(readsFrom(localStorage.getItem(RECENT_KEY)))
      if (localStorage.getItem(LOOK_KEY) === 'list') setLook('list')
    } catch {}
  }, [])

  function choose(next: Look) {
    setLook(next)
    try {
      localStorage.setItem(LOOK_KEY, next)
    } catch {}
  }

  const byId = new Map(books.map((book) => [book.id, book]))
  const recent = reads.flatMap((read) => byId.get(read.bookId) ?? [])
  const latest = reads.find((read) => byId.has(read.bookId))
  const listed = matching(filter === 'all' ? books : recent, query)

  return (
    <div className="scroll">
      <div className="lib">
        <div className="lib-head">
          <h1>
            내 서재<span className="count">{books.length}</span>
          </h1>
          <Link className="btn primary" href="/books/new">
            <Icon name="plus" size={16} />책 추가
          </Link>
        </div>
        {latest && <Continue read={latest} book={byId.get(latest.bookId) as BookSummary} />}
        <section className="shelf" aria-labelledby="shelf-all">
          <div className="shelf-bar">
            <div className="tabs">
              <button
                id="shelf-all"
                type="button"
                aria-pressed={filter === 'all'}
                onClick={() => setFilter('all')}
              >
                모든 책<span>{books.length}</span>
              </button>
              <button
                type="button"
                aria-pressed={filter === 'recent'}
                onClick={() => setFilter('recent')}
              >
                최근 읽은 책
              </button>
            </div>
            <div className="shelf-tools">
              <label className="shelf-search">
                <Icon name="search" size={16} />
                <input
                  type="search"
                  aria-label="내 책장 검색"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                />
              </label>
              <div className="looks">
                <button
                  type="button"
                  aria-label="격자로 보기"
                  aria-pressed={look === 'grid'}
                  onClick={() => choose('grid')}
                >
                  <Icon name="grid" size={16} />
                </button>
                <button
                  type="button"
                  aria-label="목록으로 보기"
                  aria-pressed={look === 'list'}
                  onClick={() => choose('list')}
                >
                  <Icon name="list" size={16} />
                </button>
              </div>
            </div>
          </div>
          {listed.length > 0 ? (
            <ul className={`shelf-books ${look}`}>
              {listed.map((book) => (
                <li key={book.id}>
                  <BookCard book={book} />
                </li>
              ))}
            </ul>
          ) : books.length === 0 ? (
            <div className="lib-empty">
              <Icon name="library" size={28} />
              <h2>책이 없습니다</h2>
              <Link className="btn primary" href="/books/new">
                책 추가
              </Link>
            </div>
          ) : (
            <div className="lib-empty">
              <Icon name="search" size={28} />
              <h2>{query.trim() ? '검색 결과가 없습니다' : '최근 읽은 책이 없습니다'}</h2>
              <button
                type="button"
                className="btn sec"
                onClick={() => {
                  setQuery('')
                  setFilter('all')
                }}
              >
                모든 책 보기
              </button>
            </div>
          )}
        </section>
        <footer className="lib-foot">Gloss / 개인 서재</footer>
      </div>
    </div>
  )
}

/** The item last read, to go back to where it was left. */
export function Continue({ read, book }: { read: RecentRead; book: BookSummary }) {
  const at = String(read.position).padStart(2, '0')
  return (
    <section className="continue-row" aria-label="최근 활동">
      <Link className="continue" href={readerHref(book.id, read.itemKey)}>
        <div className="continue-copy">
          <span className="sec-label">
            <span className="live-dot" aria-hidden="true" />
            최근 읽은 글
          </span>
          <h2>{read.title}</h2>
          <p>
            {book.title}
            <span aria-hidden="true">·</span>
            {placeOf(read)}
          </p>
          <span className="continue-cta">
            이어서 읽기
            <Icon name="arrowR" size={16} />
          </span>
        </div>
        <div className="continue-art" aria-hidden="true">
          <div className="sheet back" />
          <div className="sheet">
            <span>{book.title}</span>
            <strong>
              {at}
              <i>/</i>
            </strong>
            <b>{read.title}</b>
            <div />
            <div />
          </div>
        </div>
      </Link>
    </section>
  )
}

function BookCard({ book }: { book: BookSummary }) {
  return (
    <Link className="book-card" href={`/books/${book.id}`}>
      <div className="book-stage">
        <Cover book={book} />
        <span className="open-book" aria-hidden="true">
          <Icon name="arrowR" size={16} />
        </span>
      </div>
      <div className="book-details">
        <h3>{book.title}</h3>
        <p>{authorsLine(book.authors)}</p>
        {book.publisher && (
          <div className="book-meta">
            <span>{book.publisher}</span>
          </div>
        )}
      </div>
    </Link>
  )
}

/** A jacket drawn from the book's title and authors, in its shelf tone. */
export function Cover({ book }: { book: Pick<BookSummary, 'id' | 'title' | 'authors'> }) {
  return (
    <div className={`jacket tone-${toneOf(book.id)}`} aria-hidden="true">
      <div className="jacket-top">
        <span>gloss</span>
      </div>
      <strong>{book.title}</strong>
      <div className="jacket-art">
        <span>{monogramOf(book.title)}</span>
        <i />
        <i />
        <i />
        <i />
        <i />
      </div>
      <span className="jacket-author">{authorsLine(book.authors)}</span>
    </div>
  )
}
