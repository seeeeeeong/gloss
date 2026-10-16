'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { send } from '@/lib/api'
import { AuthError, AuthField } from './AuthField'

/** Checks the two new passwords agree, then asks the server; `onChanged` runs once it took. */
function usePasswordChange(onChanged: () => void) {
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const target = event.currentTarget
    const form = new FormData(target)
    setError(null)
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
    onChanged()
  }

  const clear = () => setError(null)
  return { error, pending, submit, clear }
}

/** The first sign-in with a temporary password, which leads on to the app once done. */
export function PasswordChangeForm() {
  const router = useRouter()
  const { error, pending, submit } = usePasswordChange(() => {
    router.replace('/')
    router.refresh()
  })
  return (
    <form onSubmit={submit}>
      <div className="fields">
        <AuthField
          name="currentPassword"
          label="임시 비밀번호"
          type="password"
          autoComplete="current-password"
          required
        />
        <AuthField
          name="newPassword"
          label="새 비밀번호"
          type="password"
          autoComplete="new-password"
          minLength={10}
          required
        />
        <AuthField
          name="confirm"
          label="새 비밀번호 확인"
          type="password"
          autoComplete="new-password"
          required
        />
        {error && <AuthError message={error} />}
      </div>
      <button type="submit" className="go" disabled={pending}>
        바꾸고 계속
      </button>
    </form>
  )
}

/** My page's password row: 바꾸기 opens the form in place. */
export function PasswordSetting() {
  const [open, setOpen] = useState(false)
  const [done, setDone] = useState(false)
  const { error, pending, submit, clear } = usePasswordChange(() => {
    setOpen(false)
    setDone(true)
  })

  return (
    <div className="srow">
      <div className="s-line">
        <span className="s-k">
          비밀번호
          {done && (
            <span className="s-sub" role="status">
              비밀번호를 바꿨습니다
            </span>
          )}
        </span>
        {!open && (
          <button
            type="button"
            className="btn sec sm"
            onClick={() => {
              setDone(false)
              clear()
              setOpen(true)
            }}
          >
            바꾸기
          </button>
        )}
      </div>
      {open && (
        <form className="s-form" onSubmit={submit}>
          <label className="f-l">
            현재 비밀번호
            <input
              className="field"
              name="currentPassword"
              type="password"
              autoComplete="current-password"
              required
            />
          </label>
          <label className="f-l">
            새 비밀번호
            <input
              className="field"
              name="newPassword"
              type="password"
              autoComplete="new-password"
              minLength={10}
              required
            />
          </label>
          <label className="f-l">
            새 비밀번호 확인
            <input
              className="field"
              name="confirm"
              type="password"
              autoComplete="new-password"
              required
            />
          </label>
          {error && (
            <p className="f-err" role="alert">
              {error}
            </p>
          )}
          <div className="actions">
            <button type="submit" className="btn primary sm" disabled={pending}>
              저장
            </button>
            <button
              type="button"
              className="btn sec sm"
              disabled={pending}
              onClick={() => {
                clear()
                setOpen(false)
              }}
            >
              취소
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
