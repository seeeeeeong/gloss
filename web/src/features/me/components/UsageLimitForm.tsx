'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { LIMIT_ERROR, limitText, parseLimit, type Usage } from '@/features/me/api/usage'
import { formatUsd } from '@/features/me/utils/money'
import { send } from '@/lib/api'
import { holdImeEnter } from '@/utils/composer'

function monthLabel(billingMonth: string): string {
  const [year, month] = billingMonth.split('-')
  return `${year}년 ${Number(month)}월`
}

/** The spent and in-flight shares of the limit, as percentages of the bar. */
function shares(usage: Usage): { spent: number; reserved: number } | null {
  if (!usage.limitMicros) return null
  const of = (micros: number) => Math.min(100, (micros / (usage.limitMicros ?? 1)) * 100)
  const spent = of(usage.spentMicros)
  return { spent, reserved: Math.min(100 - spent, of(usage.inFlightMicros)) }
}

export function UsageLimitForm({ usage }: { usage: Usage }) {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  const field = useRef<HTMLInputElement>(null)
  const edit = useRef<HTMLButtonElement>(null)
  const returning = useRef(false)

  // Opening selects the amount to retype; closing hands focus back to 수정.
  useEffect(() => {
    if (editing) field.current?.select()
    else if (returning.current) edit.current?.focus()
    returning.current = false
  }, [editing])

  // The row sits low on the page; its error line would otherwise appear out of sight.
  useEffect(() => {
    if (error) document.getElementById('limit-error')?.scrollIntoView({ block: 'nearest' })
  }, [error])

  function close() {
    returning.current = true
    setError(null)
    setEditing(false)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const micros = parseLimit(value)
    if (micros === null) {
      setError(LIMIT_ERROR)
      field.current?.focus()
      return
    }
    setError(null)
    setPending(true)
    const result = await send<Usage>('PUT', '/api/me/usage/limit', { monthlyLimitMicros: micros })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      field.current?.focus()
      return
    }
    close()
    router.refresh()
  }

  const bar = shares(usage)
  const limit = usage.limitMicros
  return (
    <>
      <p className="month">{monthLabel(usage.billingMonth)}</p>
      {bar && limit !== null && (
        <div
          className="ubar"
          role="img"
          aria-label={`월 한도 ${formatUsd(limit)} 중 사용 ${formatUsd(usage.spentMicros)}, 진행 중 ${formatUsd(usage.inFlightMicros)}`}
        >
          <i className="spent" style={{ width: `${bar.spent}%` }} />
          <i className="res" style={{ width: `${bar.reserved}%` }} />
        </div>
      )}
      <Figure swatch="spent" label="사용" micros={usage.spentMicros} />
      <Figure swatch="res" label="진행 중" micros={usage.inFlightMicros} />
      {limit !== null && usage.remainingMicros !== null && (
        <Figure swatch="left" label="남은 한도" micros={Math.max(0, usage.remainingMicros)} />
      )}
      <div className="srow">
        {editing ? (
          <form className="s-line limit-line" onSubmit={save} onKeyDown={holdImeEnter} noValidate>
            <label className="s-k" htmlFor="limit">
              월 한도
            </label>
            <span className="inl">
              <span className="money">
                <span aria-hidden="true">$</span>
                <input
                  ref={field}
                  id="limit"
                  className="field limit"
                  name="limit"
                  inputMode="decimal"
                  autoComplete="off"
                  value={value}
                  onChange={(event) => setValue(event.target.value)}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={error ? 'limit-error' : undefined}
                />
              </span>
              <button type="submit" className="btn primary sm" disabled={pending}>
                저장
              </button>
              <button type="button" className="btn sec sm" disabled={pending} onClick={close}>
                취소
              </button>
            </span>
          </form>
        ) : (
          <div className="s-line">
            <span className="s-k">월 한도</span>
            <span className={limit === null ? 's-v none' : 's-v'}>
              {limit === null ? '없음' : formatUsd(limit)}
            </span>
            <button
              ref={edit}
              type="button"
              className="btn sec sm"
              aria-label="월 한도 수정"
              onClick={() => {
                setValue(limit === null ? '' : limitText(limit))
                setError(null)
                setEditing(true)
              }}
            >
              수정
            </button>
          </div>
        )}
        {error && (
          <div className="s-form">
            <p className="f-err" id="limit-error" role="alert">
              {error}
            </p>
          </div>
        )}
      </div>
    </>
  )
}

function Figure(props: { swatch: string; label: string; micros: number }) {
  return (
    <div className="srow fig">
      <div className="s-line">
        <span className="s-k">
          <span className={`sw ${props.swatch}`} aria-hidden="true" />
          {props.label}
        </span>
        <span className="s-v">{formatUsd(props.micros)}</span>
      </div>
    </div>
  )
}
