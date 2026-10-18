import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { AdminAccount } from '@/features/admin/api/accounts'
import { AdminAccounts, AskDialog } from '@/features/admin/views/AdminAccounts'

vi.mock('next/navigation', () => ({ useRouter: () => ({ refresh() {} }) }))

const accountWith = (changes: Partial<AdminAccount>): AdminAccount => ({
  id: 'a1',
  loginId: 'haneul',
  displayName: '김하늘',
  role: 'member',
  status: 'active',
  mustChangePassword: false,
  createdAt: '2026-10-01T00:00:00Z',
  lastLoginAt: null,
  ...changes,
})

describe('AdminAccounts', () => {
  it('leaves an account in use plain and marks one waiting or turned off with an icon', () => {
    const out = renderToStaticMarkup(
      <AdminAccounts
        accounts={[
          accountWith({ id: 'a1' }),
          accountWith({ id: 'a2', mustChangePassword: true }),
          accountWith({ id: 'a3', status: 'disabled' }),
        ]}
        myId="operator"
      />,
    )

    expect(out).toContain('<td class="td c-status">사용 중</td>')
    expect(out).toMatch(/<span class="st warn"><svg[\s\S]*?<\/svg>비밀번호 변경 대기<\/span>/)
    expect(out).toMatch(/<span class="st off"><svg[\s\S]*?<\/svg>사용 중지<\/span>/)
  })

  it('starts a disable confirmation on 취소 and a password reset on 초기화', () => {
    const ask = (action: 'disable' | 'password-reset') =>
      renderToStaticMarkup(
        <AskDialog
          asking={{ action, account: accountWith({}) }}
          onCancel={() => {}}
          onConfirm={() => {}}
        />,
      )

    expect(ask('disable')).toMatch(/<button[^>]*data-autofocus[^>]*>취소<\/button>/)
    expect(ask('disable')).not.toMatch(/<button[^>]*data-autofocus[^>]*>사용 중지<\/button>/)
    expect(ask('password-reset')).toMatch(/<button[^>]*data-autofocus[^>]*>초기화<\/button>/)
  })
})
