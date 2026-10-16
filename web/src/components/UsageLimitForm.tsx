'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { send } from '@/lib/api'
import { dollarsToMicros, formatUsd } from '@/lib/money'
import type { Usage } from '@/lib/usage'

const MICROS = 1_000_000

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
  const [value, setValue] = useState(
    usage.limitMicros === null ? '' : String(usage.limitMicros / MICROS),
  )

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const micros = dollarsToMicros(value)
    if (micros === null) {
      setError('0보다 큰 금액을 달러로 입력해 주세요')
      return
    }
    setPending(true)
    const result = await send<Usage>('PUT', '/api/me/usage/limit', { monthlyLimitMicros: micros })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    // What the server kept, so the field never shows a value that was not saved.
    if (result.data.limitMicros !== null) setValue(String(result.data.limitMicros / MICROS))
    setEditing(false)
    router.refresh()
  }

  const bar = shares(usage)
  return (
    <>
      <p className="month">{monthLabel(usage.billingMonth)}</p>
      {bar && (
        <div className="ubar" aria-hidden="true">
          <i className="spent" style={{ width: `${bar.spent}%` }} />
          <i className="res" style={{ width: `${bar.reserved}%` }} />
        </div>
      )}
      <Figure swatch="spent" label="사용" micros={usage.spentMicros} />
      <Figure swatch="res" label="진행 중" micros={usage.inFlightMicros} />
      {usage.remainingMicros !== null && (
        <Figure swatch="left" label="남은 한도" micros={usage.remainingMicros} />
      )}
      <div className="srow">
        {editing ? (
          <form className="s-form" onSubmit={save}>
            <div className="s-line">
              <label className="s-k" htmlFor="limit">
                월 한도
              </label>
            </div>
            <div className="inl">
              <span className="money">
                <span aria-hidden="true">$</span>
                <input
                  id="limit"
                  className="field"
                  name="limit"
                  inputMode="decimal"
                  autoComplete="off"
                  disabled={pending}
                  value={value}
                  onChange={(event) => setValue(event.target.value)}
                  required
                />
              </span>
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
        ) : (
          <div className="s-line">
            <span className="s-k">월 한도</span>
            <span className={usage.limitMicros === null ? 's-v none' : 's-v'}>
              {usage.limitMicros === null ? '없음' : formatUsd(usage.limitMicros)}
            </span>
            <button
              type="button"
              className="btn sec sm"
              onClick={() => {
                setError(null)
                setEditing(true)
              }}
            >
              수정
            </button>
          </div>
        )}
      </div>
    </>
  )
}

function Figure(props: { swatch: string; label: string; micros: number }) {
  return (
    <div className="srow">
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
