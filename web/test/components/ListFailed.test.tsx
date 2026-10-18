import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ListFailed } from '@/components/ListFailed'

describe('ListFailed', () => {
  it('shows why a list could not be asked for, and a quiet way to ask again', () => {
    const out = renderToStaticMarkup(
      <ListFailed message="지금은 책 검색을 쓸 수 없습니다" onRetry={() => {}} />,
    )

    expect(out).toContain('지금은 책 검색을 쓸 수 없습니다')
    expect(out).toMatch(/<button type="button" class="tbtn">다시 시도<\/button>/)
  })
})
