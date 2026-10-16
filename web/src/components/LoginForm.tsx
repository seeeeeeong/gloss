'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { send } from '@/lib/api'
import type { Me } from '@/lib/session'
import { AuthError, AuthField } from './AuthField'

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

  const invalid = error !== null
  return (
    <form onSubmit={submit}>
      <div className="fields">
        <AuthField
          name="loginId"
          label="아이디"
          autoComplete="username"
          autoCapitalize="off"
          spellCheck={false}
          invalid={invalid}
          required
        />
        <AuthField
          name="password"
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          invalid={invalid}
          required
        />
        {error && <AuthError message={error} />}
      </div>
      <button type="submit" className="go" disabled={pending}>
        계속
      </button>
    </form>
  )
}
