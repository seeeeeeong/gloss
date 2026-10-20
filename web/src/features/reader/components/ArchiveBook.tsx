'use client'

import { useState } from 'react'
import { Dialog } from '@/components/ui/Dialog'
import { Icon } from '@/components/ui/Icon'
import { send } from '@/lib/api'
import { bookPath } from '@/lib/books'

const LABEL = '서재에서 빼기'

/** 서재에서 빼기, asked first with 취소 focused; the shelf opens, read again, once the book is off it. */
export function ArchiveBook({ bookId }: { bookId: string }) {
  const [asking, setAsking] = useState(false)
  const [sending, setSending] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)

  async function archive() {
    setSending(true)
    const result = await send<void>('DELETE', bookPath(bookId))
    // Already off the shelf, from another tab, is off it all the same.
    if (result.ok || result.status === 404) {
      location.replace('/')
      return
    }
    setSending(false)
    setFailed(result.message)
  }

  return (
    <>
      <button
        type="button"
        className="ibtn sm tert"
        aria-label={LABEL}
        data-tip={LABEL}
        onClick={() => {
          setFailed(null)
          setAsking(true)
        }}
      >
        <Icon name="trash" />
      </button>
      {asking && (
        <Dialog
          labelledBy="archive-book-title"
          onClose={() => setAsking(false)}
          alert
          look="confirm"
        >
          <h2 id="archive-book-title">서재에서 뺄까요?</h2>
          {failed && (
            <p className="dlg-p" role="alert">
              {failed}
            </p>
          )}
          <div className="dlg-stack">
            <button
              type="button"
              className="btn danger"
              disabled={sending}
              onClick={() => void archive()}
            >
              빼기
            </button>
            <button
              type="button"
              className="btn sec"
              data-autofocus
              onClick={() => setAsking(false)}
            >
              취소
            </button>
          </div>
        </Dialog>
      )}
    </>
  )
}
