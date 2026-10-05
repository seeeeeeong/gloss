'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { send } from '@/lib/api'

/** forced: the first sign-in with a temporary password, which leads on to the app once done. */
export function PasswordChangeForm({ forced = false }: { forced?: boolean }) {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const target = event.currentTarget
    const form = new FormData(target)
    setError(null)
    setDone(false)
    if (form.get('newPassword') !== form.get('confirm')) {
      setError('새 비밀번호가 서로 다릅니다')
      return
    }
    setPending(true)
    const result = await send('PUT', '/api/me/password', {
      currentPassword: form.get('currentPassword'),
      newPassword: form.get('newPassword'),
    })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    target.reset()
    if (forced) {
      router.replace('/')
      router.refresh()
    } else {
      setDone(true)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field">
        <span className="field-label">{forced ? '임시 비밀번호' : '현재 비밀번호'}</span>
        <input
          className="input"
          name="currentPassword"
          type="password"
          autoComplete="current-password"
          required
        />
      </label>
      <label className="field">
        <span className="field-label">새 비밀번호</span>
        <input
          className="input"
          name="newPassword"
          type="password"
          autoComplete="new-password"
          minLength={10}
          required
        />
        <span className="field-hint">10자 이상, 흔한 비밀번호는 쓸 수 없습니다</span>
      </label>
      <label className="field">
        <span className="field-label">새 비밀번호 확인</span>
        <input
          className="input"
          name="confirm"
          type="password"
          autoComplete="new-password"
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
          비밀번호를 바꿨습니다. 다른 기기의 로그인은 끊겼습니다.
        </p>
      )}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? '바꾸는 중…' : '비밀번호 바꾸기'}
        </button>
      </div>
    </form>
  )
}
