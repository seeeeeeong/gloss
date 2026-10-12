'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useRef, useState } from 'react'
import {
  type AdminAccount,
  type CreatedAccount,
  formatDateTime,
  roleLabel,
  statusLabel,
} from '@/lib/accounts'
import { send } from '@/lib/api'
import { Icon } from './Icon'

type Handed = { seq: number; loginId: string; temporaryPassword: string }

export function AdminAccounts({ accounts, myId }: { accounts: AdminAccount[]; myId: string }) {
  const router = useRouter()
  const [handed, setHanded] = useState<Handed | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const issued = useRef(0)
  // The password on screen is shown only once, so a second one waits until it is handed over.
  const issuing = pending || handed !== null

  function hand(loginId: string, temporaryPassword: string) {
    issued.current += 1
    setHanded({ seq: issued.current, loginId, temporaryPassword })
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const target = event.currentTarget
    const form = new FormData(target)
    setPending(true)
    setError(null)
    const result = await send<CreatedAccount>('POST', '/api/admin/accounts', {
      loginId: form.get('loginId'),
      displayName: form.get('displayName'),
      role: form.get('role'),
    })
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    target.reset()
    hand(result.data.account.loginId, result.data.temporaryPassword)
    router.refresh()
  }

  async function act(account: AdminAccount, action: 'password-reset' | 'disable' | 'enable') {
    if (
      action === 'disable' &&
      !window.confirm(`${account.loginId} 계정을 정지할까요? 로그인이 바로 끊깁니다.`)
    )
      return
    if (
      action === 'password-reset' &&
      !window.confirm(`${account.loginId}의 비밀번호를 리셋할까요?`)
    )
      return
    setPending(true)
    setError(null)
    const result = await send<{ temporaryPassword: string }>(
      'POST',
      `/api/admin/accounts/${account.id}/${action}`,
    )
    setPending(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    if (action === 'password-reset' && result.data) {
      hand(account.loginId, result.data.temporaryPassword)
    }
    router.refresh()
  }

  return (
    <>
      {handed && (
        <TemporaryPassword key={handed.seq} handed={handed} onDone={() => setHanded(null)} />
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <section className="section" aria-labelledby="create">
        <h2 className="section-title" id="create">
          계정 만들기
        </h2>
        <form className="form form-row" onSubmit={create}>
          <label className="field">
            <span className="field-label">아이디</span>
            <input className="input" name="loginId" autoComplete="off" required />
          </label>
          <label className="field">
            <span className="field-label">이름</span>
            <input className="input" name="displayName" autoComplete="off" required />
          </label>
          <label className="field">
            <span className="field-label">역할</span>
            <select className="input" name="role" defaultValue="member">
              <option value="member">회원</option>
              <option value="operator">운영자</option>
            </select>
          </label>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={issuing}>
              만들기
            </button>
          </div>
        </form>
      </section>
      <section className="section" aria-labelledby="list">
        <h2 className="section-title" id="list">
          계정 {accounts.length}개
        </h2>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">아이디</th>
                <th scope="col">이름</th>
                <th scope="col">역할</th>
                <th scope="col">상태</th>
                <th scope="col">마지막 로그인</th>
                <th scope="col">
                  <span className="sr-only">동작</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>
                    {account.loginId}
                    {account.id === myId && <span className="tag">나</span>}
                  </td>
                  <td>{account.displayName}</td>
                  <td>{roleLabel(account.role)}</td>
                  <td>
                    {statusLabel(account.status)}
                    {account.mustChangePassword && account.status === 'active' && (
                      <span className="tag">변경 대기</span>
                    )}
                  </td>
                  <td className="num">{formatDateTime(account.lastLoginAt)}</td>
                  <td className="row-actions">
                    {/* Resetting or disabling yourself ends this session before the password is seen. */}
                    {account.id !== myId && (
                      <>
                        <button
                          type="button"
                          className="btn btn-ghost btn-sm"
                          disabled={issuing}
                          onClick={() => act(account, 'password-reset')}
                        >
                          비밀번호 리셋
                        </button>
                        {account.status === 'active' ? (
                          <button
                            type="button"
                            className="btn btn-ghost btn-sm"
                            disabled={pending}
                            onClick={() => act(account, 'disable')}
                          >
                            정지
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="btn btn-ghost btn-sm"
                            disabled={pending}
                            onClick={() => act(account, 'enable')}
                          >
                            해제
                          </button>
                        )}
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}

function TemporaryPassword({ handed, onDone }: { handed: Handed; onDone: () => void }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    await navigator.clipboard.writeText(handed.temporaryPassword)
    setCopied(true)
  }

  return (
    <div className="handed" role="status">
      <p className="handed-title">{handed.loginId}의 임시 비밀번호</p>
      <p className="handed-note">
        이 화면을 벗어나면 다시 볼 수 없습니다. 받는 사람에게 직접 전해 주세요.
      </p>
      <div className="handed-row">
        <code className="handed-secret">{handed.temporaryPassword}</code>
        <button type="button" className="btn btn-ghost btn-sm" onClick={copy}>
          <Icon name="copy" size={16} />
          {copied ? '복사했습니다' : '복사'}
        </button>
        <button type="button" className="btn btn-ghost btn-sm" onClick={onDone}>
          전달했습니다
        </button>
      </div>
    </div>
  )
}
