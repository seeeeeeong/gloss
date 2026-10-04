import type { Metadata } from 'next'
import { PasswordChangeForm } from '@/components/PasswordChangeForm'
import { ProfileForm } from '@/components/ProfileForm'
import { requireReader } from '@/lib/server-session'

export const metadata: Metadata = { title: '마이페이지' }

export default async function MePage() {
  const me = await requireReader('/me')
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
    </div>
  )
}
