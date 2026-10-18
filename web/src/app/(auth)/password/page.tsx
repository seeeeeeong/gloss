import type { Metadata } from 'next'
import { PasswordChangeForm } from '@/features/auth/components/PasswordChangeForm'
import { SignOutButton } from '@/features/auth/components/SignOutButton'
import { guard } from '@/lib/server-session'

export const metadata: Metadata = { title: '비밀번호 바꾸기' }

export default async function PasswordPage() {
  await guard('/password')
  return (
    <>
      <h1>비밀번호를 바꿔 주세요</h1>
      <PasswordChangeForm />
      <div className="a-aside">
        <SignOutButton className="tbtn" />
      </div>
    </>
  )
}
