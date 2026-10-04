import type { Metadata } from 'next'
import { LoginForm } from '@/components/LoginForm'
import { guard } from '@/lib/server-session'

export const metadata: Metadata = { title: '로그인' }

export default async function LoginPage() {
  await guard('/login')
  return (
    <>
      <h1 className="page-title">로그인</h1>
      <p className="page-lead">계정은 운영자가 만들어 드립니다.</p>
      <LoginForm />
    </>
  )
}
