'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { send } from '@/lib/api'
import type { Me } from '@/lib/session'

export function ProfileForm({ displayName }: { displayName: string }) {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [pending, setPending] = useState(false)
  const [value, setValue] = useState(displayName)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(null)
    setDone(false)
    const result = await send<Me>('PUT', '/api/me', { displayName: value })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    // The server trims it; show what was saved.
    setValue(result.data.displayName)
    setDone(true)
    router.refresh()
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field">
        <span className="field-label">이름</span>
        <input
          className="input"
          name="displayName"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          required
        />
      </label>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {done && (
        <p className="form-done" role="status">
          이름을 바꿨습니다.
        </p>
      )}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={pending}>
          저장
        </button>
      </div>
    </form>
  )
}
