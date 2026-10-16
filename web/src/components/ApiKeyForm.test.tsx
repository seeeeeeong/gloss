import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { ApiKeyForm } from './ApiKeyForm'

vi.mock('next/navigation', () => ({ useRouter: () => ({ replace() {}, refresh() {} }) }))

describe('ApiKeyForm', () => {
  it('says in words that no key is registered, and offers nothing to delete', () => {
    const out = renderToStaticMarkup(<ApiKeyForm apiKey={null} />)

    expect(out).toContain('등록된 키가 없어요.')
    expect(out).toContain('placeholder="sk-..."')
    expect(out).toContain('키 등록')
    expect(out).not.toContain('API 키 삭제')
  })

  it('shows a registered key by its last four, with a way to delete it', () => {
    const apiKey = {
      provider: 'deepseek',
      last4: 'a3f9',
      status: 'valid',
      validatedAt: null,
      createdAt: '2026-10-15T00:13:00Z',
    }
    const out = renderToStaticMarkup(<ApiKeyForm apiKey={apiKey} />)

    expect(out).toContain('····a3f9')
    expect(out).toContain('새 키로 바꾸기')
    expect(out).toContain('API 키 삭제')
  })
})
