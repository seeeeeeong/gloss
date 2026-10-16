'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useState } from 'react'
import { formatDateTime } from '@/lib/accounts'
import { send } from '@/lib/api'
import { type ApiKey, keyStatusLabel } from '@/lib/apiKey'

export function ApiKeyForm({ apiKey }: { apiKey: ApiKey | null }) {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [value, setValue] = useState('')

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(null)
    setDone(null)
    const result = await send<ApiKey>('PUT', '/api/me/api-key', { key: value })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    // Write-only: the field empties, and the key is never shown again.
    setValue('')
    setDone('키를 확인하고 저장했습니다.')
    router.refresh()
  }

  async function remove() {
    if (!window.confirm('API 키를 삭제할까요? 튜터와 글 생성이 멈춥니다.')) return
    setPending(true)
    setError(null)
    setDone(null)
    const result = await send<void>('DELETE', '/api/me/api-key')
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    setDone('키를 삭제했습니다.')
    router.refresh()
  }

  return (
    <>
      <div className="srow">
        <div className="s-line">
          <span className="s-k">
            DeepSeek API 키
            {apiKey && (
              <span className="s-sub">
                <span className={apiKey.status === 'valid' ? 'ok' : undefined}>
                  {keyStatusLabel(apiKey.status)}
                </span>
                {apiKey.validatedAt && ` · ${formatDateTime(apiKey.validatedAt)} 확인`}
              </span>
            )}
          </span>
          {apiKey ? (
            <span className="s-v">····{apiKey.last4}</span>
          ) : (
            <span className="s-v none">없음</span>
          )}
        </div>
      </div>
      <div className="srow">
        <form className="s-form" onSubmit={save}>
          <div className="s-line">
            <label className="s-k" htmlFor="api-key">
              {apiKey ? '새 키로 바꾸기' : '키 등록'}
            </label>
          </div>
          <div className="inl">
            <input
              id="api-key"
              className="field"
              name="key"
              type="password"
              placeholder="sk-…"
              autoComplete="off"
              spellCheck={false}
              disabled={pending}
              value={value}
              onChange={(event) => setValue(event.target.value)}
              required
            />
            <button type="submit" className="btn primary" disabled={pending}>
              {pending ? '확인 중' : '저장'}
            </button>
          </div>
          {error && (
            <p className="f-err" role="alert">
              {error}
            </p>
          )}
          {done && (
            <p className="f-ok" role="status">
              {done}
            </p>
          )}
        </form>
      </div>
      {apiKey && (
        <div className="srow">
          <div className="s-line">
            <span className="s-k">API 키 삭제</span>
            <button type="button" className="btn danger-t" disabled={pending} onClick={remove}>
              삭제
            </button>
          </div>
        </div>
      )}
    </>
  )
}
