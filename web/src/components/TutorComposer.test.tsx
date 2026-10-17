import { createRef } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { TutorComposer } from './TutorComposer'

const noop = () => {}

function render(props: Partial<Parameters<typeof TutorComposer>[0]>) {
  return renderToStaticMarkup(
    <TutorComposer
      value=""
      onChange={noop}
      quote={null}
      onUnquote={noop}
      answering={false}
      onSend={noop}
      onStop={noop}
      input={createRef()}
      {...props}
    />,
  )
}

describe('TutorComposer', () => {
  it('starts as one bare line whose send waits for text', () => {
    const out = render({})
    expect(out).toContain('rows="1"')
    expect(out).not.toContain('placeholder')
    expect(out).toMatch(/<button type="submit"[^>]*aria-label="보내기"[^>]*disabled=""/)
  })

  it('shows the picked phrase above the text with its x', () => {
    const out = render({ quote: { text: '자원 할당자로', itemKey: 'a' } })
    expect(out).toContain('class="composer has-quote"')
    expect(out).toContain('<p>자원 할당자로</p>')
    expect(out).toContain('aria-label="인용 지우기"')
  })

  it('turns send into stop while Gloss answers', () => {
    const out = render({ answering: true, value: '다음 질문' })
    expect(out).toContain('class="cbtn c-send stop" aria-label="중지"')
    expect(out).not.toContain('aria-label="보내기"')
  })
})
