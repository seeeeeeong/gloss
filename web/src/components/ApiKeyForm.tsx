'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useRef, useState } from 'react'
import { formatDateTime } from '@/lib/accounts'
import { send } from '@/lib/api'
import { type ApiKey, keyStatusLabel } from '@/lib/apiKey'
import { holdImeEnter } from '@/lib/composer'
import { Dialog } from './Dialog'
import { Icon } from './Icon'

export function ApiKeyForm({ apiKey }: { apiKey: ApiKey | null }) {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [removed, setRemoved] = useState(false)
  const [pending, setPending] = useState<'save' | 'remove' | null>(null)
  const [asking, setAsking] = useState(false)
  const [value, setValue] = useState('')
  const field = useRef<HTMLInputElement>(null)
  const removeButton = useRef<HTMLButtonElement>(null)

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setDone(null)
    setRemoved(false)
    if (!value.trim()) {
      setError('키를 입력해 주세요.')
      field.current?.focus()
      return
    }
    setPending('save')
    setError(null)
    const result = await send<ApiKey>('PUT', '/api/me/api-key', { key: value.trim() })
    setPending(null)
    if (!result.ok) {
      setError(result.message)
      return
    }
    // Write-only: the field empties, and the key is never shown again.
    setValue('')
    setDone('키를 확인하고 저장했습니다.')
    router.refresh()
  }

  function cancelRemove() {
    setAsking(false)
    removeButton.current?.focus()
  }

  async function remove() {
    setAsking(false)
    setPending('remove')
    setError(null)
    setDone(null)
    const result = await send<void>('DELETE', '/api/me/api-key')
    setPending(null)
    if (!result.ok) {
      setError(result.message)
      // Back on 삭제 once this render enables it again, so another try is one key away.
      requestAnimationFrame(() => removeButton.current?.focus())
      return
    }
    setRemoved(true)
    router.refresh()
    // After this render, when the field is enabled again.
    requestAnimationFrame(() => field.current?.focus())
  }

  return (
    <>
      <KeyStatus apiKey={apiKey} />
      <div className="srow">
        <div className="s-line">
          <span className="s-k">{apiKey ? '새 키로 바꾸기' : '키 등록'}</span>
        </div>
        <form className="s-form" onSubmit={save} onKeyDown={holdImeEnter} noValidate>
          <span className="inl">
            <input
              ref={field}
              id="api-key"
              className="field"
              name="key"
              type="password"
              autoComplete="off"
              spellCheck={false}
              aria-label={apiKey ? '새 API 키' : 'API 키'}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? 'api-key-error' : undefined}
              disabled={pending !== null}
              value={value}
              onChange={(event) => setValue(event.target.value)}
            />
            <button type="submit" className="btn primary sm" disabled={pending !== null}>
              {pending === 'save' ? '확인 중…' : '저장'}
            </button>
          </span>
          {error && (
            <p className="f-err" id="api-key-error" role="alert">
              {error}
            </p>
          )}
          {done && (
            <p className="f-ok" role="status">
              {done}
            </p>
          )}
          <span className="sr-only" role="status">
            {removed ? 'API 키를 삭제했습니다.' : ''}
          </span>
        </form>
      </div>
      {apiKey && (
        <div className="srow">
          <div className="s-line">
            <span className="s-k">API 키 삭제</span>
            <button
              ref={removeButton}
              type="button"
              className="btn danger-t"
              disabled={pending !== null}
              onClick={() => setAsking(true)}
            >
              삭제
            </button>
          </div>
        </div>
      )}
      {asking && <RemoveKeyDialog onRemove={remove} onCancel={cancelRemove} />}
    </>
  )
}

// A key in use stays plain: only one to enter again or one DeepSeek refused gets a tone.
const KEY_TONES: Record<string, string> = { unreadable: 'warn', invalid: 'bad' }

function KeyStatus({ apiKey }: { apiKey: ApiKey | null }) {
  const tone = apiKey ? KEY_TONES[apiKey.status] : undefined
  return (
    <div className="srow">
      <div className="s-line">
        <span className="s-k">
          DeepSeek API 키
          {apiKey && (
            <span className={tone ? `s-sub s-state ${tone}` : 's-sub s-state'}>
              <Icon name={tone ? 'alert' : 'check'} size={14} />
              {keyStatusLabel(apiKey.status)}
            </span>
          )}
          {apiKey?.validatedAt && (
            <span className="s-sub">{formatDateTime(apiKey.validatedAt)} 확인</span>
          )}
        </span>
        {apiKey ? (
          <span className="s-v">{`····${apiKey.last4}`}</span>
        ) : (
          <span className="s-v s-state warn">
            <Icon name="alert" size={14} />
            등록된 키가 없어요.
          </span>
        )}
      </div>
    </div>
  )
}

export function RemoveKeyDialog({
  onRemove,
  onCancel,
}: {
  onRemove: () => void
  onCancel: () => void
}) {
  return (
    <Dialog labelledBy="remove-key-title" onClose={onCancel} alert look="confirm">
      <button type="button" className="ibtn x" aria-label="닫기" onClick={onCancel}>
        <Icon name="x" />
      </button>
      <h2 id="remove-key-title">API 키를 삭제할까요?</h2>
      <p className="dlg-p">튜터와 글 생성이 멈춥니다.</p>
      <div className="dlg-stack">
        <button type="button" className="btn danger" onClick={onRemove}>
          삭제
        </button>
        <button type="button" className="btn sec" data-autofocus onClick={onCancel}>
          취소
        </button>
      </div>
    </Dialog>
  )
}
