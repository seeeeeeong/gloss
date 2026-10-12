'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, type ReactNode, useState } from 'react'
import { send } from '@/lib/api'
import type { Book, Toc } from '@/lib/books'

type Props = {
  bookId: string
  toc: Pick<Toc, 'id' | 'status' | 'raw'>
  children: ReactNode
}

export function TocReview({ bookId, toc, children }: Props) {
  const router = useRouter()
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const draft = toc.status === 'draft'

  function edit() {
    setValue(toc.raw)
    setError(null)
    setDone(null)
    setEditing(true)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(null)
    setDone(null)
    const result = await send<Book>('POST', `/api/me/books/${bookId}/toc`, { toc: value })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    setEditing(false)
    setDone('새 버전으로 저장했습니다.')
    router.refresh()
  }

  async function confirm() {
    setPending(true)
    setError(null)
    setDone(null)
    const result = await send<Book>('POST', `/api/me/books/${bookId}/toc/${toc.id}/confirm`)
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      // Someone saved a newer version elsewhere: show it, since that is the one to confirm.
      if (result.code === 'book.toc_outdated') router.refresh()
      return
    }
    setDone('목차를 확정했습니다.')
    router.refresh()
  }

  const messages = (
    <>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {done && (
        <p className="form-done" role="status">
          {done}
        </p>
      )}
    </>
  )

  if (editing) {
    return (
      <form className="form" onSubmit={save}>
        <label className="field">
          <span className="field-label">목차 고치기</span>
          <textarea
            className="input textarea"
            spellCheck={false}
            disabled={pending}
            value={value}
            onChange={(event) => setValue(event.target.value)}
            required
          />
        </label>
        {messages}
        <div className="form-actions">
          <button
            type="button"
            className="btn btn-ghost"
            disabled={pending}
            onClick={() => setEditing(false)}
          >
            취소
          </button>
          <button type="submit" className="btn btn-primary" disabled={pending}>
            {pending ? '저장 중…' : '저장'}
          </button>
        </div>
      </form>
    )
  }

  return (
    <div className="form">
      {messages}
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" disabled={pending} onClick={edit}>
          고치기
        </button>
        {draft && (
          <button type="button" className="btn btn-primary" disabled={pending} onClick={confirm}>
            {pending ? '확정 중…' : '확정'}
          </button>
        )}
      </div>
      <div className="toc">{children}</div>
    </div>
  )
}
