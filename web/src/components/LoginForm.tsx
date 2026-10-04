'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { send } from '@/lib/api'
import type { Me } from '@/lib/session'

export function LoginForm() {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setPending(true)
    setError(null)
    const result = await send<Me>('POST', '/api/auth/login', {
      loginId: form.get('loginId'),
      password: form.get('password'),
    })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    router.replace(result.data.mustChangePassword ? '/password' : '/')
    router.refresh()
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field">
        <span className="field-label">아이디</span>
        <input className="input" name="loginId" autoComplete="username" required />
      </label>
      <label className="field">
        <span className="field-label">비밀번호</span>
        <input
          className="input"
          name="password"
          type="password"
          autoComplete="current-password"
          required
        />
      </label>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="btn btn-primary" disabled={pending}>
        {pending ? '로그인 중…' : '로그인'}
      </button>
    </form>
  )
}
