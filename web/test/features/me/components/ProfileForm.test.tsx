import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { ProfileForm } from '@/features/me/components/ProfileForm'

vi.mock('next/navigation', () => ({ useRouter: () => ({ replace() {}, refresh() {} }) }))

describe('ProfileForm', () => {
  it('names what its button edits', () => {
    const out = renderToStaticMarkup(<ProfileForm displayName="김민준" />)

    expect(out).toContain('김민준')
    expect(out).toContain('aria-label="이름 수정"')
  })
})
