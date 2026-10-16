'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { send } from '@/lib/api'
import { holdImeEnter } from '@/lib/composer'
import {
  checkPasswordChange,
  type PasswordErrors,
  type PasswordField,
  passwordFieldOf,
} from '@/lib/password'
import { HOME } from '@/lib/session'
import { AuthError, AuthField } from './AuthField'

const FIELDS: PasswordField[] = ['current', 'next', 'confirm']
const NAMES: Record<PasswordField, string> = {
  current: 'currentPassword',
  next: 'newPassword',
  confirm: 'confirm',
}

const errorId = (field: PasswordField) => `password-${field}-error`

/** Checks the form, then asks the server; each error lands under its field. */
function usePasswordChange(place: 'first' | 'settings', onChanged: () => void) {
  const [errors, setErrors] = useState<PasswordErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  function focus(form: HTMLFormElement, field: PasswordField) {
    ;(form.elements.namedItem(NAMES[field]) as HTMLInputElement | null)?.focus()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const target = event.currentTarget
    const data = new FormData(target)
    const value = (field: PasswordField) => String(data.get(NAMES[field]) ?? '')
    const found = checkPasswordChange(
      { current: value('current'), next: value('next'), confirm: value('confirm') },
      place,
    )
    setErrors(found)
    setFormError(null)
    const first = FIELDS.find((field) => found[field])
    if (first) {
      focus(target, first)
      return
    }
    setPending(true)
    const result = await send('PUT', '/api/me/password', {
      currentPassword: value('current'),
      newPassword: value('next'),
    })
    setPending(false)
    if (!result.ok) {
      const field = passwordFieldOf(result.code)
      if (field) {
        setErrors({ [field]: result.message })
        focus(target, field)
      } else {
        setFormError(result.message)
      }
      return
    }
    target.reset()
    onChanged()
  }

  const clear = () => {
    setErrors({})
    setFormError(null)
  }
  // The field's mark points at the line under it.
  const marks = (field: PasswordField) => ({
    'aria-invalid': errors[field] ? true : undefined,
    'aria-describedby': errors[field] ? errorId(field) : undefined,
  })
  return { errors, formError, pending, submit, clear, marks }
}

/** The first sign-in with a temporary password, which leads on to the app once done. */
export function PasswordChangeForm() {
  const router = useRouter()
  const { errors, formError, pending, submit } = usePasswordChange('first', () => {
    router.replace(HOME)
    router.refresh()
  })
  const field = (name: PasswordField) =>
    errors[name] && <AuthError id={errorId(name)} message={errors[name]} />
  return (
    <form onSubmit={submit} onKeyDown={holdImeEnter} noValidate>
      <div className="fields">
        <AuthField
          name={NAMES.current}
          label="임시 비밀번호"
          type="password"
          autoComplete="current-password"
          invalid={Boolean(errors.current)}
          describedBy={errorId('current')}
        />
        {field('current')}
        <AuthField
          name={NAMES.next}
          label="새 비밀번호"
          type="password"
          autoComplete="new-password"
          invalid={Boolean(errors.next)}
          describedBy={errorId('next')}
        />
        {field('next')}
        <AuthField
          name={NAMES.confirm}
          label="새 비밀번호 확인"
          type="password"
          autoComplete="new-password"
          invalid={Boolean(errors.confirm)}
          describedBy={errorId('confirm')}
        />
        {field('confirm')}
        {formError && <AuthError message={formError} />}
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
  const opener = useRef<HTMLButtonElement>(null)
  const current = useRef<HTMLInputElement>(null)
  // Closing hands focus back to 바꾸기 once it is on the page again.
  const returning = useRef(false)
  const { errors, formError, pending, submit, clear, marks } = usePasswordChange('settings', () =>
    close(true),
  )

  useEffect(() => {
    if (open) current.current?.focus()
    else if (returning.current) opener.current?.focus()
    returning.current = false
  }, [open])

  function close(changed: boolean) {
    clear()
    setDone(changed)
    returning.current = true
    setOpen(false)
  }

  const error = (field: PasswordField) =>
    errors[field] && (
      // Announced, since the field it belongs to already had focus when it appeared.
      <p className="f-err" id={errorId(field)} role="alert">
        {errors[field]}
      </p>
    )
  return (
    <div className="srow">
      <div className="s-line">
        <span className="s-k">
          비밀번호
          {done && (
            <span className="s-sub" role="status">
              방금 바꿨습니다.
            </span>
          )}
        </span>
        {!open && (
          <button
            ref={opener}
            type="button"
            className="btn sec sm"
            aria-label="비밀번호 바꾸기"
            aria-expanded="false"
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
        <form className="s-form" onSubmit={submit} onKeyDown={holdImeEnter} noValidate>
          <label className="f-l">
            현재 비밀번호
            <input
              ref={current}
              className="field"
              name={NAMES.current}
              type="password"
              autoComplete="current-password"
              {...marks('current')}
            />
          </label>
          {error('current')}
          <label className="f-l">
            새 비밀번호
            <input
              className="field"
              name={NAMES.next}
              type="password"
              autoComplete="new-password"
              {...marks('next')}
            />
          </label>
          {error('next')}
          <label className="f-l">
            새 비밀번호 확인
            <input
              className="field"
              name={NAMES.confirm}
              type="password"
              autoComplete="new-password"
              {...marks('confirm')}
            />
          </label>
          {error('confirm')}
          {formError && (
            <p className="f-err" role="alert">
              {formError}
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
              onClick={() => close(false)}
            >
              취소
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
