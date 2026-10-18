import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { Cover } from '@/components/ui/Cover'

const BOOK = { id: 'a', title: '운영체제', authors: ['Abraham Silberschatz', 'Peter B. Galvin'] }

describe('Cover', () => {
  it("draws the title, its first letter large and the authors, in the book's tone", () => {
    const out = renderToStaticMarkup(<Cover book={BOOK} />)
    expect(out).toMatch(/<div class="jacket tone-(sage|clay|ink|apricot)" aria-hidden="true">/)
    expect(out).toContain('<strong>운영체제</strong>')
    expect(out).toContain('<div class="jacket-art"><span>운</span>')
    expect(out).toContain(
      '<span class="jacket-author">Abraham Silberschatz, Peter B. Galvin</span>',
    )
  })
})
