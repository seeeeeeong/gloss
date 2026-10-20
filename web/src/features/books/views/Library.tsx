'use client'

import './library.css'
import Link from 'next/link'
import { useEffect, useState } from 'react'
import { Cover } from '@/components/ui/Cover'
import { Icon } from '@/components/ui/Icon'
import { type Bookmark, markText } from '@/lib/bookmarks'
import { authorsLine, type BookSummary, editionParts } from '@/lib/books'
import { readerHref } from '@/lib/posts'
import { matching, placeOf, type RecentRead } from '@/lib/shelf'

type Filter = 'all' | 'recent'
type Look = 'grid' | 'list'

const LOOK_KEY = 'gloss.shelf.look'

type Props = {
  books: BookSummary[]
  /** The books read lately, newest first. */
  recent?: string[]
  /** The item read last, to go back to. */
  latest?: RecentRead
  /** The bookmark kept last, shown beside the item last read. */
  mark?: Bookmark
}

/**
 * 내 서재: the item last read to go back to and the bookmark kept last, then every book, or the ones
 * read lately, as covers or as a list, narrowed by a search.
 */
export function Library({ books, recent = [], latest, mark }: Props) {
  const [filter, setFilter] = useState<Filter>('all')
  const [look, setLook] = useState<Look>('grid')
  const [query, setQuery] = useState('')

  // How the reader likes the shelf lives in this browser, so it arrives after the shelf renders.
  useEffect(() => {
    try {
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
  const read = recent.flatMap((bookId) => byId.get(bookId) ?? [])
  const listed = matching(filter === 'all' ? books : read, query)

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
        <Recent read={latest} book={latest ? byId.get(latest.bookId) : undefined} mark={mark} />
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
                  placeholder="제목이나 저자"
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
          ) : (
            <Nothing
              shelved={books.length > 0}
              searched={query.trim() !== ''}
              onAll={() => {
                setQuery('')
                setFilter('all')
              }}
            />
          )}
        </section>
        <footer className="lib-foot">Gloss / 개인 서재</footer>
      </div>
    </div>
  )
}

type RecentProps = { read?: RecentRead; book?: BookSummary; mark?: Bookmark }

/** What the reader did last: the item last read and the bookmark kept last, side by side. */
function Recent({ read, book, mark }: RecentProps) {
  const reading = read && book ? { read, book } : null
  if (!reading && !mark) return null
  return (
    <section
      className={reading && mark ? 'continue-row two' : 'continue-row'}
      aria-label="최근 활동"
    >
      {reading && <Continue read={reading.read} book={reading.book} />}
      {mark && <LastMark mark={mark} />}
    </section>
  )
}

type NothingProps = { shelved: boolean; searched: boolean; onAll: () => void }

/** An empty shelf offers 책 추가; a search or a tab that finds nothing offers every book again. */
function Nothing({ shelved, searched, onAll }: NothingProps) {
  if (!shelved)
    return (
      <div className="lib-empty">
        <Icon name="library" size={28} />
        <h2>책이 없습니다</h2>
        <Link className="btn primary" href="/books/new">
          책 추가
        </Link>
      </div>
    )
  return (
    <div className="lib-empty">
      <Icon name="search" size={28} />
      <h2>{searched ? '검색 결과가 없습니다' : '최근 읽은 책이 없습니다'}</h2>
      <button type="button" className="btn sec" onClick={onAll}>
        모든 책 보기
      </button>
    </div>
  )
}

/** The item last read, to go back to where it was left. */
export function Continue({ read, book }: { read: RecentRead; book: BookSummary }) {
  const at = String(read.position).padStart(2, '0')
  return (
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
  )
}

/** The bookmark kept last: its phrase and its book, opening every bookmark. */
function LastMark({ mark }: { mark: Bookmark }) {
  return (
    <Link className="last-mark" href="/bookmarks">
      <span className="sec-label">
        <Icon name="bookmark" size={15} />
        책갈피
      </span>
      <blockquote>{markText(mark)}</blockquote>
      <span className="last-mark-from">
        {mark.bookTitle}
        <Icon name="arrowR" size={16} />
      </span>
    </Link>
  )
}

function BookCard({ book }: { book: BookSummary }) {
  const edition = editionParts(book)
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
        {edition.length > 0 && (
          <div className="book-meta">
            {edition.map((part) => (
              <span key={part}>{part}</span>
            ))}
          </div>
        )}
      </div>
    </Link>
  )
}
