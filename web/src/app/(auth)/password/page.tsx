import type { Metadata } from 'next'
import { PasswordChangeForm } from '@/components/PasswordChangeForm'
import { SignOutButton } from '@/components/SignOutButton'
import { guard } from '@/lib/server-session'

export const metadata: Metadata = { title: '비밀번호 바꾸기' }

export default async function PasswordPage() {
  await guard('/password')
  return (
    <>
      <h1 className="page-title">비밀번호를 바꿔 주세요</h1>
      <p className="page-lead">
        운영자가 준 임시 비밀번호로 로그인했습니다. 계속하려면 새 비밀번호를 정해 주세요.
      </p>
      <PasswordChangeForm forced />
      <p className="page-lead auth-aside">
        내 계정이 아니거나 나중에 바꾸려면 로그아웃할 수 있습니다. <SignOutButton />
      </p>
    </>
  )
}
