import type { Metadata } from 'next'
import { LoginForm } from '@/components/LoginForm'
import { guard } from '@/lib/server-session'

export const metadata: Metadata = { title: '로그인' }

export default async function LoginPage() {
  await guard('/login')
  return (
    <>
      <h1 className="page-title">로그인</h1>
      <LoginForm />
    </>
  )
}
