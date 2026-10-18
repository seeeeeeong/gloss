'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { send } from '@/lib/api'
import type { Me } from '@/lib/session'
import { holdImeEnter } from '@/utils/composer'

/** My page's name row: 수정 turns the value into a field in the same row. */
export function ProfileForm({ displayName }: { displayName: string }) {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [editing, setEditing] = useState(false)
  const [saved, setSaved] = useState(displayName)
  const [value, setValue] = useState(displayName)
  const field = useRef<HTMLInputElement>(null)
  const edit = useRef<HTMLButtonElement>(null)
  const returning = useRef(false)

  // Opening selects the name to retype; closing hands focus back to 수정.
  useEffect(() => {
    if (editing) field.current?.select()
    else if (returning.current) edit.current?.focus()
    returning.current = false
  }, [editing])

  function close() {
    returning.current = true
    setEditing(false)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(null)
    const result = await send<Me>('PUT', '/api/me', { displayName: value })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      field.current?.focus()
      return
    }
    // The server trims it; show what was saved.
    setSaved(result.data.displayName)
    close()
    router.refresh()
  }

  if (!editing) {
    return (
      <div className="srow">
        <div className="s-line">
          <span className="s-k">이름</span>
          <span className="s-v">{saved}</span>
          <button
            ref={edit}
            type="button"
            className="btn sec sm"
            aria-label="이름 수정"
            onClick={() => {
              setValue(saved)
              setError(null)
              setEditing(true)
            }}
          >
            수정
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="srow">
      <form className="s-line" onSubmit={submit} onKeyDown={holdImeEnter}>
        <label className="s-k fit" htmlFor="display-name">
          이름
        </label>
        <span className="inl grow">
          <input
            ref={field}
            id="display-name"
            className="field"
            name="displayName"
            autoComplete="name"
            value={value}
            onChange={(event) => setValue(event.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? 'display-name-error' : undefined}
          />
          <button type="submit" className="btn primary sm" disabled={pending}>
            저장
          </button>
          <button type="button" className="btn sec sm" disabled={pending} onClick={close}>
            취소
          </button>
        </span>
      </form>
      {error && (
        <div className="s-form">
          <p className="f-err" id="display-name-error" role="alert">
            {error}
          </p>
        </div>
      )}
    </div>
  )
}
