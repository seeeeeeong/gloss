import type { Metadata } from 'next'
import { cookies } from 'next/headers'
import { ApiKeyForm } from '@/components/ApiKeyForm'
import { Page } from '@/components/Page'
import { PasswordSetting } from '@/components/PasswordChangeForm'
import { ProfileForm } from '@/components/ProfileForm'
import { SignOutButton } from '@/components/SignOutButton'
import { ThemeSelect } from '@/components/ThemeSelect'
import { UsageLimitForm } from '@/components/UsageLimitForm'
import type { ApiKey } from '@/lib/apiKey'
import { requireReader, serverFind, serverGet } from '@/lib/server-session'
import { THEME_COOKIE, themeFrom } from '@/lib/theme'
import type { Usage } from '@/lib/usage'

export const metadata: Metadata = { title: '마이페이지' }

export default async function MePage() {
  const me = await requireReader('/me')
  const [apiKey, usage, jar] = await Promise.all([
    serverFind<ApiKey>('/api/me/api-key'),
    serverGet<Usage>('/api/me/usage'),
    cookies(),
  ])
  const theme = themeFrom(jar.get(THEME_COOKIE)?.value)
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
      </section>
      <section className="set" aria-labelledby="display">
        <h2 className="set-h" id="display">
          화면
        </h2>
        <div className="srow">
          <div className="s-line">
            <span className="s-k" id="theme-label">
              테마
            </span>
            <ThemeSelect initial={theme} />
          </div>
        </div>
      </section>
    </Page>
  )
}
