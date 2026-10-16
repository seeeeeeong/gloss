'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { send } from '@/lib/api'
import { holdImeEnter } from '@/lib/composer'
import { emptyLoginField, lockLeft } from '@/lib/login'
import { HOME, type Me } from '@/lib/session'
import { AuthError, AuthField } from './AuthField'

type Field = 'loginId' | 'password'
type Lock = { message: string; until: number }

const AUTH_ERROR = 'auth-error'
const errorId = (field: Field) => `${field}-error`

function credentialsOf(form: HTMLFormElement) {
  const values = new FormData(form)
  return {
    loginId: String(values.get('loginId') ?? ''),
    password: String(values.get('password') ?? ''),
  }
}

export function LoginForm() {
  const router = useRouter()
  const form = useRef<HTMLFormElement>(null)
  const [empty, setEmpty] = useState<{ field: Field; message: string } | null>(null)
  const [refused, setRefused] = useState<string | null>(null)
  const [lock, setLock] = useState<Lock | null>(null)
  const [left, setLeft] = useState(0)
  const [pending, setPending] = useState(false)

  // Counts the lock down each second, and lifts it at zero.
  useEffect(() => {
    if (!lock) return
    const tick = () => {
      const seconds = Math.ceil((lock.until - Date.now()) / 1000)
      if (seconds <= 0) setLock(null)
      else setLeft(seconds)
    }
    tick()
    const timer = setInterval(tick, 1000)
    return () => clearInterval(timer)
  }, [lock])

  const input = (name: Field) => form.current?.elements.namedItem(name) as HTMLInputElement | null

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (lock || pending) return
    const { loginId, password } = credentialsOf(event.currentTarget)
    setRefused(null)
    const missing = emptyLoginField(loginId, password)
    setEmpty(missing)
    if (missing) {
      input(missing.field)?.focus()
      return
    }
    setPending(true)
    const result = await send<Me>('POST', '/api/auth/login', { loginId, password })
    setPending(false)
    if (result.ok) enter(result.data)
    else refuse(result)
  }

  function enter(me: Me) {
    router.replace(me.mustChangePassword ? '/password' : HOME)
    router.refresh()
  }

  function refuse(result: { code: string; message: string; retryAfter?: number }) {
    if (result.code === 'account.too_many_attempts' && result.retryAfter) {
      setLeft(result.retryAfter)
      setLock({ message: result.message, until: Date.now() + result.retryAfter * 1000 })
      return
    }
    setRefused(result.message)
    input('password')?.focus()
    input('password')?.select()
  }

  // Typing again takes the marks away; a lock stays until it runs out.
  function edit() {
    setEmpty(null)
    setRefused(null)
  }

  const marked = (field: Field) => refused !== null || empty?.field === field
  const describedBy = (field: Field) => (empty?.field === field ? errorId(field) : AUTH_ERROR)
  return (
    <form ref={form} onSubmit={submit} onInput={edit} onKeyDown={holdImeEnter} noValidate>
      <div className="fields">
        <AuthField
          name="loginId"
          label="아이디"
          autoComplete="username"
          autoCapitalize="off"
          spellCheck={false}
          invalid={marked('loginId')}
          describedBy={describedBy('loginId')}
        />
        {empty?.field === 'loginId' && (
          <AuthError id={errorId('loginId')} message={empty.message} />
        )}
        <AuthField
          name="password"
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          invalid={marked('password')}
          describedBy={describedBy('password')}
        />
        {empty?.field === 'password' && (
          <AuthError id={errorId('password')} message={empty.message} />
        )}
        {refused && <AuthError id={AUTH_ERROR} message={refused} />}
        {lock && (
          <AuthError id={AUTH_ERROR} message={lock.message}>
            {/* Announced once with the message, not again every second. */}
            <span aria-live="off"> {lockLeft(left)}</span>
          </AuthError>
        )}
      </div>
      <button type="submit" className="go" disabled={pending || lock !== null}>
        계속
      </button>
    </form>
  )
}
