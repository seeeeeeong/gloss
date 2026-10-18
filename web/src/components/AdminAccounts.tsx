'use client'

import { useRouter } from 'next/navigation'
import { type FormEvent, useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import {
  type AdminAccount,
  type CreatedAccount,
  formatDateTime,
  roleLabel,
  statusLabel,
} from '@/lib/accounts'
import { send } from '@/lib/api'
import { holdImeEnter } from '@/lib/composer'
import { COPIED_FOR } from '@/lib/copied'
import { moveInMenu } from '@/lib/menu'
import { Dialog } from './Dialog'
import { Icon } from './Icon'
import { useDismiss } from './useDismiss'

type Handed = { seq: number; temporaryPassword: string; reset: boolean }
type Action = 'password-reset' | 'disable' | 'enable'
type Asking = { action: 'password-reset' | 'disable'; account: AdminAccount }

const ROLES = ['member', 'operator'] as const

const statusOf = (account: AdminAccount) =>
  account.status === 'active' && account.mustChangePassword
    ? '비밀번호 변경 대기'
    : statusLabel(account.status)

export function AdminAccounts({ accounts, myId }: { accounts: AdminAccount[]; myId: string }) {
  const router = useRouter()
  const [handed, setHanded] = useState<Handed | null>(null)
  const [creating, setCreating] = useState(false)
  const [asking, setAsking] = useState<Asking | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const issued = useRef(0)
  // The password on screen is shown only once, so a second one waits until it is handed over.
  const issuing = pending || handed !== null

  function hand(temporaryPassword: string, reset: boolean) {
    issued.current += 1
    setHanded({ seq: issued.current, temporaryPassword, reset })
  }

  async function act(account: AdminAccount, action: Action) {
    setAsking(null)
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
      hand(result.data.temporaryPassword, true)
    }
    router.refresh()
  }

  return (
    <div className="accounts">
      <div className="p-row">
        <h1 className="title">계정</h1>
        <button
          type="button"
          className="btn primary"
          disabled={issuing}
          onClick={() => setCreating(true)}
        >
          계정 만들기
        </button>
      </div>
      {error && (
        <p className="f-err banner" role="alert">
          {error}
        </p>
      )}
      <table className="tbl">
        <thead>
          <tr className="tr th">
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
            <AccountRow
              key={account.id}
              account={account}
              mine={account.id === myId}
              busy={pending}
              issuing={issuing}
              onAct={(action) =>
                action === 'enable' ? act(account, action) : setAsking({ action, account })
              }
            />
          ))}
        </tbody>
      </table>
      {creating && (
        <CreateDialog
          onClose={() => setCreating(false)}
          onCreated={(created) => {
            setCreating(false)
            hand(created.temporaryPassword, false)
            router.refresh()
          }}
        />
      )}
      {asking && (
        <AskDialog
          asking={asking}
          onCancel={() => setAsking(null)}
          onConfirm={() => act(asking.account, asking.action)}
        />
      )}
      {handed && (
        <TemporaryPassword key={handed.seq} handed={handed} onDone={() => setHanded(null)} />
      )}
    </div>
  )
}

export function AskDialog({
  asking,
  onCancel,
  onConfirm,
}: {
  asking: Asking
  onCancel: () => void
  onConfirm: () => void
}) {
  const disable = asking.action === 'disable'
  return (
    <Dialog labelledBy="ask-title" onClose={onCancel} alert look="plain">
      {disable ? (
        <>
          <h2 id="ask-title">{asking.account.displayName} 님의 계정을 사용 중지할까요?</h2>
          <p className="dlg-p">로그인한 기기에서도 바로 로그아웃돼요.</p>
        </>
      ) : (
        <h2 id="ask-title">{asking.account.displayName} 님의 비밀번호를 초기화할까요?</h2>
      )}
      <div className="dlg-a">
        {/* A destructive choice is never the default: Enter on the open dialog cancels it. */}
        <button
          type="button"
          className="btn sec"
          data-autofocus={disable || undefined}
          onClick={onCancel}
        >
          취소
        </button>
        <button
          type="button"
          className={disable ? 'btn danger' : 'btn primary'}
          data-autofocus={disable ? undefined : true}
          onClick={onConfirm}
        >
          {disable ? '사용 중지' : '초기화'}
        </button>
      </div>
    </Dialog>
  )
}

// An account in use stays plain; one waiting on its password or turned off says so with an icon.
function AccountStatus({ account }: { account: AdminAccount }) {
  const label = statusOf(account)
  if (account.status !== 'active')
    return (
      <span className="st off">
        <Icon name="ban" size={16} />
        {label}
      </span>
    )
  if (!account.mustChangePassword) return label
  return (
    <span className="st warn">
      <Icon name="clock" size={16} />
      {label}
    </span>
  )
}

type RowProps = {
  account: AdminAccount
  mine: boolean
  busy: boolean
  issuing: boolean
  onAct: (action: Action) => void
}

function AccountRow({ account, mine, busy, issuing, onAct }: RowProps) {
  const [open, setOpen] = useState<'mouse' | 'keyboard' | null>(null)
  const [up, setUp] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const menu = useRef<HTMLDivElement>(null)
  const close = useCallback(() => setOpen(null), [])
  useDismiss(open !== null, root, button, close)
  const active = account.status === 'active'

  // Under the button, or over it when the window ends first.
  useLayoutEffect(() => {
    const box = menu.current
    const anchor = button.current
    if (!open || !box || !anchor) return
    setUp(anchor.getBoundingClientRect().bottom + 4 + box.offsetHeight > innerHeight - 8)
  }, [open])

  // Opened from the keyboard, the menu takes focus on its first item.
  useEffect(() => {
    if (open === 'keyboard')
      menu.current?.querySelector<HTMLElement>('[role="menuitem"]:not(:disabled)')?.focus()
  }, [open])

  function choose(action: Action) {
    setOpen(null)
    // Focus waits on the row's button, so a dialog that follows hands it back there.
    button.current?.focus()
    onAct(action)
  }

  const frame = ['tr m', !active && 'off', open && 'open'].filter(Boolean).join(' ')
  return (
    <tr className={frame}>
      <td className="who">
        <span className="nm">{account.displayName}</span>
        <span className="lid">{account.loginId}</span>
        <span className="lid meta-l" aria-hidden="true">
          {roleLabel(account.role)} · {statusOf(account)}
        </span>
      </td>
      <td className="td c-role">{roleLabel(account.role)}</td>
      <td className="td c-status">
        <AccountStatus account={account} />
      </td>
      <td className="td c-last">
        {account.lastLoginAt ? (
          formatDateTime(account.lastLoginAt)
        ) : (
          <span className="none">{formatDateTime(null)}</span>
        )}
      </td>
      <td className="td act">
        <div ref={root}>
          {/* Resetting or disabling yourself ends this session before the password is seen. */}
          {!mine && (
            <>
              <button
                ref={button}
                type="button"
                className="ibtn sm"
                aria-haspopup="menu"
                aria-expanded={open !== null}
                aria-label={`${account.displayName} 계정 메뉴`}
                disabled={busy}
                onClick={(event) =>
                  setOpen(open ? null : event.detail === 0 ? 'keyboard' : 'mouse')
                }
              >
                <Icon name="more" />
              </button>
              {open && (
                <div
                  ref={menu}
                  className={up ? 'menu up' : 'menu'}
                  role="menu"
                  aria-label={`${account.displayName} 계정 메뉴`}
                  onKeyDown={(event) => {
                    if (event.key === 'Tab') close()
                    else moveInMenu(event)
                  }}
                >
                  <button
                    type="button"
                    role="menuitem"
                    className="mi"
                    disabled={issuing}
                    onClick={() => choose('password-reset')}
                  >
                    <Icon name="key" size={18} />
                    비밀번호 초기화
                  </button>
                  {active ? (
                    <button
                      type="button"
                      role="menuitem"
                      className="mi danger"
                      onClick={() => choose('disable')}
                    >
                      <Icon name="ban" size={18} />
                      사용 중지
                    </button>
                  ) : (
                    <button
                      type="button"
                      role="menuitem"
                      className="mi"
                      onClick={() => choose('enable')}
                    >
                      <Icon name="restore" size={18} />
                      다시 사용
                    </button>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </td>
    </tr>
  )
}

function CreateDialog(props: { onClose: () => void; onCreated: (c: CreatedAccount) => void }) {
  const { onClose, onCreated } = props
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [filled, setFilled] = useState(false)

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
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
    onCreated(result.data)
  }

  function check(event: FormEvent<HTMLFormElement>) {
    const form = new FormData(event.currentTarget)
    const has = (name: string) => String(form.get(name) ?? '').trim() !== ''
    setFilled(has('displayName') && has('loginId'))
  }

  return (
    <Dialog labelledBy="create-title" onClose={onClose} dismissible={!pending}>
      <h2 id="create-title">계정 만들기</h2>
      <button
        type="button"
        className="ibtn x"
        aria-label="닫기"
        onClick={onClose}
        disabled={pending}
      >
        <Icon name="x" />
      </button>
      <form autoComplete="off" onSubmit={create} onInput={check} onKeyDown={holdImeEnter}>
        <div className="dlg-f">
          <div className="fld">
            <label htmlFor="new-name">이름</label>
            <input id="new-name" className="in" name="displayName" required data-autofocus />
          </div>
          <div className="fld">
            <label htmlFor="new-id">아이디</label>
            <input
              id="new-id"
              className="in"
              name="loginId"
              autoCapitalize="off"
              spellCheck={false}
              required
            />
          </div>
          <fieldset className="fld">
            <legend>역할</legend>
            <div className="seg">
              {ROLES.map((role) => (
                <label key={role}>
                  <input type="radio" name="role" value={role} defaultChecked={role === 'member'} />
                  <span>{roleLabel(role)}</span>
                </label>
              ))}
            </div>
          </fieldset>
          {error && (
            <p className="f-err" role="alert">
              {error}
            </p>
          )}
        </div>
        <div className="dlg-a">
          <button type="submit" className="btn primary" disabled={!filled || pending}>
            만들기
          </button>
        </div>
      </form>
    </Dialog>
  )
}

type Copy = 'idle' | 'copied' | 'failed'

function TemporaryPassword({ handed, onDone }: { handed: Handed; onDone: () => void }) {
  const [copy, setCopy] = useState<Copy>('idle')
  const field = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (copy === 'idle') return
    const timer = setTimeout(() => setCopy('idle'), COPIED_FOR)
    return () => clearTimeout(timer)
  }, [copy])

  async function copyPassword() {
    try {
      await navigator.clipboard.writeText(handed.temporaryPassword)
      setCopy('copied')
    } catch {
      // Selected, so it can still be copied by hand.
      field.current?.focus()
      field.current?.select()
      setCopy('failed')
    }
  }

  return (
    <Dialog labelledBy="handed-title" onClose={onDone} dismissible={false} look="plain">
      <h2 id="handed-title">{handed.reset ? '비밀번호를 초기화했어요' : '계정을 만들었어요'}</h2>
      <div className="fld pw">
        <label htmlFor="handed-password">임시 비밀번호</label>
        <div className="pw-box">
          <input
            ref={field}
            id="handed-password"
            value={handed.temporaryPassword}
            readOnly
            spellCheck={false}
          />
          <button
            type="button"
            className={copy === 'copied' ? 'cp done' : 'cp'}
            data-autofocus
            onClick={copyPassword}
          >
            <Icon name={copy === 'copied' ? 'check' : 'copy'} size={18} />
            <span aria-live="polite">
              {copy === 'copied'
                ? '복사했습니다'
                : copy === 'failed'
                  ? '복사하지 못했어요'
                  : '복사'}
            </span>
          </button>
        </div>
      </div>
      <p className="pw-note">이 비밀번호는 다시 볼 수 없어요.</p>
      <div className="dlg-a">
        <button type="button" className="btn primary" onClick={onDone}>
          확인
        </button>
      </div>
    </Dialog>
  )
}
