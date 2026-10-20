import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { TabBar } from '@/app/(app)/_components/TabBar'

describe('TabBar', () => {
  it('leads to the shelf, a new book and the bookmarks', () => {
    const out = renderToStaticMarkup(<TabBar path="/me" />)
    expect(out).toContain('<nav class="tabbar" aria-label="탭 메뉴">')
    expect(out).toMatch(/<a href="\/">.*?내 서재<\/a>/)
    expect(out).toMatch(/<a href="\/books\/new">.*?새 책<\/a>/)
    expect(out).toMatch(/<a href="\/bookmarks">.*?책갈피<\/a>/)
    expect(out).not.toContain('aria-current')
  })

  it('marks the tab of the screen open, a setup conversation under 새 책', () => {
    expect(renderToStaticMarkup(<TabBar path="/" />)).toMatch(
      /<a aria-current="page" href="\/">|<a href="\/" aria-current="page">/,
    )
    expect(renderToStaticMarkup(<TabBar path="/books/new/s1" />)).toMatch(
      /<a aria-current="page" href="\/books\/new">|<a href="\/books\/new" aria-current="page">/,
    )
  })
})
