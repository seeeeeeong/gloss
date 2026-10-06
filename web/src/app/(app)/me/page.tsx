import type { Metadata } from 'next'
import { ApiKeyForm } from '@/components/ApiKeyForm'
import { PasswordChangeForm } from '@/components/PasswordChangeForm'
import { ProfileForm } from '@/components/ProfileForm'
import { UsageLimitForm } from '@/components/UsageLimitForm'
import type { ApiKey } from '@/lib/apiKey'
import { requireReader, serverFind, serverGet } from '@/lib/server-session'
import type { Usage } from '@/lib/usage'

export const metadata: Metadata = { title: '마이페이지' }

export default async function MePage() {
  const me = await requireReader('/me')
  const [apiKey, usage] = await Promise.all([
    serverFind<ApiKey>('/api/me/api-key'),
    serverGet<Usage>('/api/me/usage'),
  ])
  return (
    <div className="page">
      <h1 className="page-title">마이페이지</h1>
      <p className="page-lead">
        {me.loginId} · {me.role === 'operator' ? '운영자' : '회원'}
      </p>
      <section className="section" aria-labelledby="profile">
        <h2 className="section-title" id="profile">
          프로필
        </h2>
        <ProfileForm displayName={me.displayName} />
      </section>
      <section className="section" aria-labelledby="password">
        <h2 className="section-title" id="password">
          비밀번호
        </h2>
        <PasswordChangeForm />
      </section>
      <section className="section" aria-labelledby="api-key">
        <h2 className="section-title" id="api-key">
          API 키
        </h2>
        <ApiKeyForm apiKey={apiKey} />
      </section>
      <section className="section" aria-labelledby="usage">
        <h2 className="section-title" id="usage">
          사용량과 한도
        </h2>
        <UsageLimitForm usage={usage} />
      </section>
    </div>
  )
}
