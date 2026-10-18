import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { SizeMenu, SizeStepper } from '@/features/reader/components/ReadingSize'

describe('SizeStepper', () => {
  it('shows the size with a step down and a step up around it', () => {
    const out = renderToStaticMarkup(<SizeStepper labelledBy="size" />)
    expect(out).toContain('<fieldset class="stepper" aria-labelledby="size">')
    expect(out).toContain('aria-label="글자 작게"')
    expect(out).toContain('<span aria-live="polite">18</span>')
    expect(out).toContain('aria-label="글자 크게"')
  })

  it('sets 가 at the size beside its number when asked to', () => {
    const out = renderToStaticMarkup(<SizeStepper labelledBy="size" sample />)
    expect(out).toContain(
      '<span class="cur" aria-live="polite"><b aria-hidden="true" style="font-size:18px">가</b>18</span>',
    )
  })
})

describe('SizeMenu', () => {
  it('starts as a closed 가 button naming what it sets', () => {
    const out = renderToStaticMarkup(<SizeMenu />)
    expect(out).toMatch(/<button [^>]*class="ibtn ga" aria-expanded="false" aria-label="글자 크기"/)
    expect(out).toContain('>가</button>')
    expect(out).not.toContain('stepper')
  })
})
