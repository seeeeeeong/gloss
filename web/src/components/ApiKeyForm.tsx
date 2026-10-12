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
    <form className="form" onSubmit={save}>
      {apiKey ? (
        <p className="key-current">
          <span className="key-last4">····{apiKey.last4}</span>
          <span className="tag">{keyStatusLabel(apiKey.status)}</span>
          <span className="field-hint">확인 {formatDateTime(apiKey.validatedAt)}</span>
        </p>
      ) : (
        <p className="notice">
          등록된 키가 없습니다. 튜터와 글 생성에 DeepSeek API 키가 필요합니다.
        </p>
      )}
      <label className="field">
        <span className="field-label">{apiKey ? '새 키로 바꾸기' : 'DeepSeek API 키'}</span>
        <input
          className="input"
          name="key"
          type="password"
          autoComplete="off"
          spellCheck={false}
          disabled={pending}
          value={value}
          onChange={(event) => setValue(event.target.value)}
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
          {done}
        </p>
      )}
      <div className="form-actions">
        {apiKey && (
          <button type="button" className="btn btn-ghost" disabled={pending} onClick={remove}>
            삭제
          </button>
        )}
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? '확인 중…' : '저장'}
        </button>
      </div>
    </form>
  )
}
