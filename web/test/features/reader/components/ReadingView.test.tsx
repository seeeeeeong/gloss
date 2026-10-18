import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ViewToggle } from '@/features/reader/components/ReadingView'

const noop = () => {}

describe('ViewToggle', () => {
  it('offers the book and the posts, the view in use pressed', () => {
    const book = renderToStaticMarkup(<ViewToggle view="book" onChoose={noop} />)
    expect(book).toContain('<fieldset class="views" aria-label="보기 방식">')
    expect(book).toMatch(/aria-pressed="true" aria-label="책으로 보기"/)
    expect(book).toMatch(/aria-pressed="false" aria-label="글로 보기"/)

    const post = renderToStaticMarkup(<ViewToggle view="post" onChoose={noop} />)
    expect(post).toMatch(/aria-pressed="false" aria-label="책으로 보기"/)
    expect(post).toMatch(/aria-pressed="true" aria-label="글로 보기"/)
  })
})
