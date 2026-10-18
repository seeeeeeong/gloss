import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ReadingSizeDialog, SizeStepper } from '@/features/reader/components/ReadingSize'

describe('SizeStepper', () => {
  it('shows the size with a step down and a step up around it', () => {
    const out = renderToStaticMarkup(<SizeStepper labelledBy="size" />)
    expect(out).toContain('<fieldset class="stepper" aria-labelledby="size">')
    expect(out).toContain('aria-label="글자 작게"')
    expect(out).toContain('<span aria-live="polite">18</span>')
    expect(out).toContain('aria-label="글자 크게"')
  })
})

describe('ReadingSizeDialog', () => {
  it('offers the three sizes with the one in use pressed, and a line of the book set in it', () => {
    const out = renderToStaticMarkup(<ReadingSizeDialog onClose={() => {}} />)
    expect(out.match(/aria-pressed="true"/g)).toHaveLength(1)
    expect(out).toMatch(/aria-pressed="true"[^>]*>.*?보통<\/button>/)
    expect(out).toContain('작게')
    expect(out).toContain('크게')
    expect(out).toContain('class="size-sample" style="font-size:18px;line-height:32px"')
  })
})
