'use client'

import './library.css'
import Link from 'next/link'
import { Icon } from '@/components/ui/Icon'
import { useBookmarks } from '@/hooks/useBookmarks'
import { type Bookmark, markText } from '@/lib/bookmarks'
import { readerHref } from '@/lib/posts'

/** 책갈피: each phrase kept, large, with the book and the item it came from, which open it again. */
export function Bookmarks({ bookmarks }: { bookmarks: Bookmark[] }) {
  const { marks: kept, said, removing, remove } = useBookmarks(bookmarks)

  return (
    <div className="scroll">
      <div className="lib">
        <div className="lib-head">
          <h1>
            책갈피<span className="count">{kept.length}</span>
          </h1>
        </div>
        {kept.length > 0 ? (
          <ul className="marks">
            {kept.map((bookmark) => (
              <li key={bookmark.id} className="mark">
                <button
                  type="button"
                  className="ibtn sm saved"
                  aria-label={`${markText(bookmark)} 책갈피 지우기`}
                  data-tip="책갈피 지우기"
                  disabled={removing.has(bookmark.id)}
                  onClick={() => void remove(bookmark)}
                >
                  <Icon name="bookmark" size={18} />
                </button>
                <blockquote>{markText(bookmark)}</blockquote>
                <Link className="mark-from" href={readerHref(bookmark.bookId, bookmark.itemKey)}>
                  <span>
                    <b>{bookmark.bookTitle}</b>
                    <small>
                      {[bookmark.itemNumber, bookmark.itemTitle].filter(Boolean).join(' ')}
                    </small>
                  </span>
                  <Icon name="arrowR" />
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <div className="lib-empty">
            <Icon name="bookmark" size={28} />
            <h2>저장한 문장이 없습니다</h2>
            <Link className="btn primary" href="/">
              글 읽기
            </Link>
          </div>
        )}
      </div>
      <div className={said ? 'toast shown' : 'toast'} role="status">
        {said}
      </div>
    </div>
  )
}
