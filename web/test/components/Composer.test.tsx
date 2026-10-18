import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { Composer } from '@/components/Composer'

const noop = () => {}

describe('Composer', () => {
  it('starts as one empty line whose send button waits for text', () => {
    const out = renderToStaticMarkup(
      <Composer label="책 제목" placeholder="책 제목" onSend={noop} />,
    )
    expect(out).toContain('rows="1"')
    expect(out).toContain('aria-label="책 제목"')
    expect(out).toMatch(/<button type="submit"[^>]*aria-label="보내기"[^>]*disabled=""/)
  })

  it('shows the left button only when a screen gives it a job', () => {
    const bare = renderToStaticMarkup(<Composer label="메시지" onSend={noop} />)
    expect(bare).toContain('class="composer no-plus"')
    expect(bare).not.toContain('c-plus')

    const plus = { label: '직접 입력', expanded: false, onClick: noop }
    const full = renderToStaticMarkup(<Composer label="메시지" onSend={noop} plus={plus} />)
    expect(full).toContain('class="composer"')
    expect(full).toContain('aria-label="직접 입력"')
    expect(full).toContain('aria-expanded="false"')
  })
})
