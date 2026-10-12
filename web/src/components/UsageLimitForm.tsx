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

export function UsageLimitForm({ usage }: { usage: Usage }) {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [pending, setPending] = useState(false)
  const [value, setValue] = useState(
    usage.limitMicros === null ? '' : String(usage.limitMicros / MICROS),
  )

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setDone(false)
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
    setDone(true)
    router.refresh()
  }

  return (
    <form className="form" onSubmit={save}>
      <dl className="figures">
        <dt>{monthLabel(usage.billingMonth)} 사용</dt>
        <dd>{formatUsd(usage.spentMicros)}</dd>
        <dt>진행 중</dt>
        <dd>{formatUsd(usage.inFlightMicros)}</dd>
        <dt>남은 한도</dt>
        <dd>{usage.remainingMicros === null ? '—' : formatUsd(usage.remainingMicros)}</dd>
      </dl>
      <label className="field">
        <span className="field-label">월 한도 (달러)</span>
        <input
          className="input"
          name="limit"
          inputMode="decimal"
          autoComplete="off"
          disabled={pending}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          required
        />
        <span className="field-hint">
          넘으면 그달에는 튜터와 글 생성이 멈춥니다. 금액은 문서 단가로 계산한 추정치이고, 실제
          청구는 DeepSeek 계정에서 확인하세요.
        </span>
      </label>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {done && (
        <p className="form-done" role="status">
          한도를 바꿨습니다.
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
