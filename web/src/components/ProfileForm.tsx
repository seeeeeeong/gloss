'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { send } from '@/lib/api'
import type { Me } from '@/lib/session'

/** My page's name row: 수정 turns the value into a field in place. */
export function ProfileForm({ displayName }: { displayName: string }) {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [editing, setEditing] = useState(false)
  const [saved, setSaved] = useState(displayName)
  const [value, setValue] = useState(displayName)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(null)
    const result = await send<Me>('PUT', '/api/me', { displayName: value })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    // The server trims it; show what was saved.
    setSaved(result.data.displayName)
    setEditing(false)
    router.refresh()
  }

  if (!editing) {
    return (
      <div className="srow">
        <div className="s-line">
          <span className="s-k">이름</span>
          <span className="s-v">{saved}</span>
          <button
            type="button"
            className="btn sec sm"
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
      <form className="s-form" onSubmit={submit}>
        <div className="s-line">
          <label className="s-k" htmlFor="display-name">
            이름
          </label>
        </div>
        <div className="inl">
          <input
            id="display-name"
            className="field"
            name="displayName"
            value={value}
            onChange={(event) => setValue(event.target.value)}
            disabled={pending}
            required
          />
          <button type="submit" className="btn primary" disabled={pending}>
            저장
          </button>
          <button
            type="button"
            className="btn sec"
            disabled={pending}
            onClick={() => setEditing(false)}
          >
            취소
          </button>
        </div>
        {error && (
          <p className="f-err" role="alert">
            {error}
          </p>
        )}
      </form>
    </div>
  )
}
