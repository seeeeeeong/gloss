import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ArchiveBook } from '@/features/reader/components/ArchiveBook'

describe('ArchiveBook', () => {
  it('is an icon until pressed, asking nothing yet', () => {
    const out = renderToStaticMarkup(<ArchiveBook bookId="b1" />)
    expect(out).toContain('aria-label="서재에서 빼기"')
    expect(out).not.toContain('<dialog')
  })
})
