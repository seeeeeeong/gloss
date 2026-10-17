import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { ApiKeyForm, RemoveKeyDialog } from './ApiKeyForm'

vi.mock('next/navigation', () => ({ useRouter: () => ({ replace() {}, refresh() {} }) }))

const keyIn = (status: string, validatedAt: string | null = null) => ({
  provider: 'deepseek',
  last4: 'a3f9',
  status,
  validatedAt,
  createdAt: '2026-10-15T00:13:00Z',
})

describe('ApiKeyForm', () => {
  it('says in words that no key is registered, and offers nothing to delete', () => {
    const out = renderToStaticMarkup(<ApiKeyForm apiKey={null} />)

    expect(out).toContain('등록된 키가 없어요.')
    expect(out).toContain('placeholder="sk-..."')
    expect(out).toContain('키 등록')
    expect(out).not.toContain('API 키 삭제')
  })

  it('shows a registered key by its last four, with a way to delete it', () => {
    const out = renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('valid')} />)

    expect(out).toContain('····a3f9')
    expect(out).toContain('새 키로 바꾸기')
    expect(out).toContain('API 키 삭제')
  })

  it('keeps a key in use plain and puts when it was checked on a line of its own', () => {
    const out = renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('valid', '2026-10-15T00:13:00Z')} />)

    expect(out).toContain('<span class="s-sub s-state">')
    expect(out).toContain('<span class="s-sub">2026-10-15 09:13 확인</span>')
    expect(out).not.toContain(' · ')
  })

  it('gives a missing key, one to enter again and one DeepSeek refused a tone', () => {
    expect(renderToStaticMarkup(<ApiKeyForm apiKey={null} />)).toContain('s-v s-state warn')
    expect(renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('unreadable')} />)).toContain(
      's-sub s-state warn',
    )
    expect(renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('invalid')} />)).toContain(
      's-sub s-state bad',
    )
  })

  it('starts the delete confirmation on 취소, not on the destructive choice', () => {
    const out = renderToStaticMarkup(<RemoveKeyDialog onRemove={() => {}} onCancel={() => {}} />)

    expect(out).toMatch(/<button[^>]*data-autofocus[^>]*>취소<\/button>/)
    expect(out).not.toMatch(/<button[^>]*data-autofocus[^>]*>삭제<\/button>/)
  })
})
