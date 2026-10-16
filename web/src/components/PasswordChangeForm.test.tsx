import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { PasswordChangeForm, PasswordSetting } from './PasswordChangeForm'

vi.mock('next/navigation', () => ({ useRouter: () => ({ replace() {}, refresh() {} }) }))

describe('PasswordChangeForm', () => {
  it('checks its own fields, so the browser shows no bubbles', () => {
    const out = renderToStaticMarkup(<PasswordChangeForm />)

    expect(out).toContain('noValidate=""')
    expect(out).not.toContain('minLength')
    expect(out).not.toContain('required')
  })
})

describe('PasswordSetting', () => {
  it('starts closed, its button named for what it opens', () => {
    const out = renderToStaticMarkup(<PasswordSetting />)

    expect(out).toContain('aria-label="비밀번호 바꾸기"')
    expect(out).not.toContain('<form')
  })
})
