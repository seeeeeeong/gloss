import { type ComponentProps, createRef } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ReaderPost } from '@/features/reader/components/ReaderPost'
import type { ItemView } from '@/features/reader/utils/reader'
import type { TocItem } from '@/lib/books'

const item = (id: string, parentId: string | null, number: string, title: string): TocItem => ({
  id,
  parentId,
  depth: parentId ? 1 : 0,
  number,
  title,
  itemKey: id,
})
const ITEMS = [
  item('c1', null, '1', '서론'),
  item('s11', 'c1', '1.1', '할 일'),
  item('s12', 'c1', '1.2', '역사'),
]
const BOOK = { id: 'b1', title: '운영체제' }
const noop = () => {}

const post = (
  at: number,
  viewOf: (key: string) => ItemView,
  markdown: string | null = null,
  book: ComponentProps<typeof ReaderPost>['book'] = BOOK,
) =>
  renderToStaticMarkup(
    <ReaderPost
      book={book}
      items={ITEMS}
      at={at}
      viewOf={viewOf}
      markdown={markdown}
      text={createRef()}
      size={20}
      onGo={noop}
      onTurn={noop}
      onRetry={noop}
    />,
  )
const written = (): ItemView => ({ kind: 'written', stale: false })

describe('ReaderPost', () => {
  it('heads the item with its place in the book and its title, then its post in the size chosen', () => {
    const out = post(1, written, '## 학습 목표\n\n본문')
    expect(out).toMatch(
      /<nav class="post-path" aria-label="위치"><span>운영체제<\/span><span><span aria-hidden="true">\/<\/span><a href="\/books\/b1\?item=c1">1 서론<\/a><\/span><\/nav>/,
    )
    expect(out).toContain('style="font-size:20px;line-height:36px"')
    expect(out).toContain('<article class="item" data-unit="1" aria-busy="false">')
    expect(out).toContain('<h1 tabindex="-1"><span class="no">1.1 </span>할 일</h1>')
    expect(out).toContain('<h2>학습 목표</h2>')
  })

  it('credits the source of the contents under the title', () => {
    const url = 'https://www.yes24.com/product/goods/101'
    const out = post(1, written, '본문', { ...BOOK, source: 'yes24', sourceUrl: url })
    expect(out).toContain(
      `</h1><p class="byline">AI 학습 콘텐츠 · 책 본문 요약 아님 · 목차 출처 <a class="dl" href="${url}" target="_blank" rel="noopener noreferrer">YES24</a></p>`,
    )
    expect(post(1, written, '본문')).toContain(
      '<p class="byline">AI 학습 콘텐츠 · 책 본문 요약 아님</p>',
    )
  })

  it('links the items before and after it', () => {
    const out = post(1, written)
    expect(out).toMatch(
      /<a class="nav-card prev" href="\/books\/b1\?item=c1"><span class="k">.*?이전<\/span><span class="v"><span class="no">1 <\/span><span>서론<\/span><\/span><\/a>/,
    )
    expect(out).toMatch(
      /<a class="nav-card next" href="\/books\/b1\?item=s12"><span class="k">.*?다음<\/span><span class="v"><span class="no">1\.2 <\/span><span>역사<\/span><\/span><\/a>/,
    )
  })

  it('keeps the next item to the right on the first, and offers none after the last', () => {
    const first = post(0, written)
    expect(first).toContain('<nav class="post-path" aria-label="위치"><span>운영체제</span></nav>')
    expect(first).toContain('<nav class="post-nav" aria-label="이전 글과 다음 글"><span></span>')
    expect(first).not.toContain('nav-card prev')
    expect(post(2, written)).not.toContain('nav-card next')
  })

  it('shows an item being written as the book view does, and marks a neighbour being written', () => {
    const out = post(1, (key) => (key === 's12' ? { kind: 'writing' } : { kind: 'todo' }))
    expect(out).toContain('aria-busy="false"')
    expect(out).toMatch(/역사<\/span><span class="spin" role="img" aria-label="쓰는 중"><\/span>/)
    const writing = post(1, () => ({ kind: 'writing' }))
    expect(writing).toContain('<span class="cur" aria-hidden="true"></span>')
  })
})
