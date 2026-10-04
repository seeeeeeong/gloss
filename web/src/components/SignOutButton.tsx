'use client'

import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { send } from '@/lib/api'

export function SignOutButton() {
  const router = useRouter()
  const [error, setError] = useState<string | null>(null)

  async function signOut() {
    setError(null)
    const result = await send('POST', '/api/auth/logout')
    // 401: the session had already ended, which is where signing out leads anyway.
    if (result.ok || result.status === 401) {
      router.replace('/login')
      router.refresh()
    } else {
      setError(result.message)
    }
  }

  return (
    <>
      {error && (
        <span className="form-error sign-out-error" role="alert">
          {error}
        </span>
      )}
      <button type="button" className="btn btn-ghost" onClick={signOut}>
        로그아웃
      </button>
    </>
  )
}
