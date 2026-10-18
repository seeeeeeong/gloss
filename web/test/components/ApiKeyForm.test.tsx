import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { ApiKeyForm, RemoveKeyDialog } from '@/components/ApiKeyForm'

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
    expect(out).toContain('키 등록')
    expect(out).not.toContain('API 키 삭제')
  })

  it('leaves the key field without a placeholder', () => {
    expect(renderToStaticMarkup(<ApiKeyForm apiKey={null} />)).not.toContain('placeholder')
  })

  it('shows a registered key by its last four, with a way to delete it in red', () => {
    const out = renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('valid')} />)

    expect(out).toContain('····a3f9')
    expect(out).toContain('새 키로 바꾸기')
    expect(out).toContain('API 키 삭제')
    expect(out).toMatch(/<button type="button" class="btn sec sm destroy">삭제<\/button>/)
  })

  it('takes a new key on one row, its label, the field and 저장', () => {
    const out = renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('valid')} />)
    expect(out).toMatch(
      /<form class="s-line"[^>]*><span class="s-k">새 키로 바꾸기<\/span><span class="inl grow"><input/,
    )
  })

  it('keeps a key in use plain and puts when it was checked on a line of its own', () => {
    const out = renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('valid', '2026-10-15T00:13:00Z')} />)

    expect(out).toMatch(/<span class="s-v"><span class="s-state"><svg[\s\S]*?<\/svg>정상<\/span>/)
    expect(out).toContain('<span class="s-sub">2026-10-15 09:13 확인</span>')
    expect(out).toContain('<span class="s-end">····a3f9</span>')
    expect(out).not.toContain(' · ')
  })

  it('gives a missing key, one to enter again and one DeepSeek refused a tone', () => {
    expect(renderToStaticMarkup(<ApiKeyForm apiKey={null} />)).toContain('s-v s-state warn')
    expect(renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('unreadable')} />)).toContain(
      'class="s-state warn"',
    )
    expect(renderToStaticMarkup(<ApiKeyForm apiKey={keyIn('invalid')} />)).toContain(
      'class="s-state bad"',
    )
  })

  it('starts the delete confirmation on 취소, not on the destructive choice', () => {
    const out = renderToStaticMarkup(<RemoveKeyDialog onRemove={() => {}} onCancel={() => {}} />)

    expect(out).toMatch(/<button[^>]*data-autofocus[^>]*>취소<\/button>/)
    expect(out).not.toMatch(/<button[^>]*data-autofocus[^>]*>삭제<\/button>/)
  })
})
