import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { LoginForm } from '@/features/auth/components/LoginForm'

vi.mock('next/navigation', () => ({ useRouter: () => ({ replace() {}, refresh() {} }) }))

describe('LoginForm', () => {
  it('checks its own fields, so the browser shows no bubbles', () => {
    const out = renderToStaticMarkup(<LoginForm />)

    expect(out).toContain('noValidate=""')
    expect(out).not.toContain('required')
    expect(out).not.toContain('aria-invalid')
  })
})
