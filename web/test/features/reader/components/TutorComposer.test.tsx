import { createRef } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { TutorComposer } from '@/features/reader/components/TutorComposer'

const noop = () => {}

function render(props: Partial<Parameters<typeof TutorComposer>[0]>) {
  return renderToStaticMarkup(
    <TutorComposer
      value=""
      onChange={noop}
      quote={null}
      onUnquote={noop}
      placeholder="이 글에 대해 물어보세요"
      answering={false}
      onSend={noop}
      onStop={noop}
      input={createRef()}
      {...props}
    />,
  )
}

describe('TutorComposer', () => {
  it('starts as one line asking for a question, whose send waits for text', () => {
    const out = render({})
    expect(out).toContain('rows="1"')
    expect(out).toContain('placeholder="이 글에 대해 물어보세요"')
    expect(out).toMatch(/<button type="submit"[^>]*aria-label="보내기"[^>]*disabled=""/)
  })

  it('shows the picked phrase on a card over the composer with its x', () => {
    const out = render({ quote: { text: '자원 할당자로', itemKey: 'a' } })
    expect(out).toMatch(
      /^<div class="t-quote"><p><mark>자원 할당자로<\/mark><\/p><button [^>]*aria-label="인용 지우기"[^>]*>.*?<\/button><\/div><form class="composer quoted"/,
    )
  })

  it('turns send into stop while Gloss answers', () => {
    const out = render({ answering: true, value: '다음 질문' })
    expect(out).toContain('class="cbtn c-send stop" aria-label="중지"')
    expect(out).not.toContain('aria-label="보내기"')
  })
})
