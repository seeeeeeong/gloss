'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'
import { chatHref, chatsByBook, loadRecent, RECENT_CHANGED, type Recent } from '@/lib/reads'
import { toneOf } from '@/lib/shelf'

type Props = {
  /** As the server listed it for the page. */
  initial: Recent
  within: (href: string) => 'page' | undefined
  onLeave: () => void
}

/**
 * 최근: the books read lately, each opening where the reader left it, with its latest conversations
 * under it, each opening the book with that conversation. Read again whenever the reader reads on,
 * or begins or deletes a conversation.
 */
export function RecentNav({ initial, within, onLeave }: Props) {
  const recent = useRecent(initial)
  if (recent.books.length === 0) return null
  const chats = chatsByBook(recent.chats)
  return (
    <>
      <div className="sb-sec" id="recent">
        최근
      </div>
      <ul className="sb-books" aria-labelledby="recent">
        {recent.books.map((book) => {
          const href = `/books/${book.bookId}`
          const talks = chats.get(book.bookId) ?? []
          return (
            <li key={book.bookId}>
              <Link
                className="row"
                href={href}
                title={book.title}
                aria-current={within(href)}
                onClick={onLeave}
              >
                <span className={`spine tone-${toneOf(book.bookId)}`} aria-hidden="true" />
                <span className="lbl-t">{book.title}</span>
              </Link>
              {talks.length > 0 && (
                <ul className="sb-chats">
                  {talks.map((chat) => (
                    <li key={chat.id}>
                      <Link
                        className="row"
                        href={chatHref(book.bookId, chat.id)}
                        title={chat.firstQuestion}
                        onClick={onLeave}
                      >
                        <span className="lbl-t">{chat.firstQuestion}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}

/** The server's list for the page, then each read since; a read overtaken by a later one is dropped. */
function useRecent(initial: Recent): Recent {
  const [recent, setRecent] = useState(initial)
  useEffect(() => setRecent(initial), [initial])
  useEffect(() => {
    let asked = 0
    const onChange = async () => {
      const mine = ++asked
      const read = await loadRecent()
      if (read && mine === asked) setRecent(read)
    }
    const listener = () => void onChange()
    addEventListener(RECENT_CHANGED, listener)
    return () => removeEventListener(RECENT_CHANGED, listener)
  }, [])
  return recent
}
