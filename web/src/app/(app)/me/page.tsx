import type { Metadata } from 'next'
import { ApiKeyForm } from '@/components/ApiKeyForm'
import { Page } from '@/components/Page'
import { PasswordSetting } from '@/components/PasswordChangeForm'
import { ProfileForm } from '@/components/ProfileForm'
import { SizeStepper } from '@/components/ReadingSize'
import { RecentCalls } from '@/components/RecentCalls'
import { SignOutButton } from '@/components/SignOutButton'
import { ThemeSwitch } from '@/components/ThemeSwitch'
import { UsageLimitForm } from '@/components/UsageLimitForm'
import { formatDateTime } from '@/lib/accounts'
import type { ApiKey } from '@/lib/apiKey'
import { requireReader, serverFind, serverGet } from '@/lib/server-session'
import type { Usage, UsageCall } from '@/lib/usage'

export const metadata: Metadata = { title: '마이페이지' }

export default async function MePage() {
  const me = await requireReader('/me')
  const [apiKey, usage, calls] = await Promise.all([
    serverFind<ApiKey>('/api/me/api-key'),
    serverGet<Usage>('/api/me/usage'),
    serverGet<UsageCall[]>('/api/me/usage/calls'),
  ])
  return (
    <Page>
      <div className="p-row">
        <h1 className="title">마이페이지</h1>
      </div>
      <section className="set" aria-labelledby="account">
        <h2 className="set-h" id="account">
          계정
        </h2>
        <ProfileForm displayName={me.displayName} />
        <div className="srow">
          <div className="s-line">
            <span className="s-k">아이디</span>
            <span className="s-v">{me.loginId}</span>
          </div>
        </div>
        <div className="srow">
          <div className="s-line">
            <span className="s-k">마지막 로그인</span>
            <span className="s-v">{formatDateTime(me.lastLoginAt)}</span>
          </div>
        </div>
        <PasswordSetting />
        <div className="srow">
          <div className="s-line">
            <span className="s-k">이 기기에서 로그아웃</span>
            <SignOutButton />
          </div>
        </div>
      </section>
      <section className="set" aria-labelledby="api-key-head">
        <h2 className="set-h" id="api-key-head">
          API 키
        </h2>
        <ApiKeyForm apiKey={apiKey} />
      </section>
      <section className="set" aria-labelledby="usage">
        <h2 className="set-h" id="usage">
          사용량
        </h2>
        <UsageLimitForm usage={usage} />
        <RecentCalls calls={calls} now={new Date()} />
      </section>
      <section className="set" aria-labelledby="display">
        <h2 className="set-h" id="display">
          화면
        </h2>
        <div className="srow">
          <div className="s-line">
            <span className="s-k" id="dark-label">
              다크 모드
            </span>
            <ThemeSwitch labelledBy="dark-label" />
          </div>
        </div>
        <div className="srow">
          <div className="s-line">
            <span className="s-k" id="size-label">
              읽기 글자 크기
            </span>
            <SizeStepper labelledBy="size-label" />
          </div>
        </div>
      </section>
    </Page>
  )
}
