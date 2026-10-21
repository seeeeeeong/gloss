import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ItemArticle } from '@/features/reader/components/ReaderPages'
import type { ItemView } from '@/features/reader/utils/reader'
import type { SourceLink } from '@/lib/books'

const noop = () => {}
const YES24 = { name: 'YES24', url: 'https://www.yes24.com/product/goods/101' }
const article = (
  view: ItemView,
  markdown: string | null = null,
  credit: SourceLink | null = YES24,
) =>
  renderToStaticMarkup(
    <ItemArticle
      unit={3}
      number="1.1"
      title="운영체제가 할 일"
      view={view}
      markdown={markdown}
      credit={credit}
      onRetry={noop}
    />,
  )
const written = (): ItemView => ({ kind: 'written', stale: false })

describe('ItemArticle', () => {
  it('shows a written post under its heading, as a unit of the flow', () => {
    const out = article({ kind: 'written', stale: false }, '## 학습 목표\n\n- 하나')
    expect(out).toMatch(/^<article class="item" data-unit="3" aria-busy="false">/)
    expect(out).toContain('<h1 tabindex="-1"><span class="no">1.1 </span>운영체제가 할 일</h1>')
    expect(out).toContain('<h2>학습 목표</h2>')
    expect(out).not.toContain('이전 목차')
  })

  it('says under the title of a written post that a model wrote it, crediting the contents', () => {
    expect(article(written(), '본문')).toContain(
      '</h1><p class="byline">AI 학습 콘텐츠 · 책 본문 요약 아님 · 목차 출처 <a class="dl" href="https://www.yes24.com/product/goods/101" target="_blank" rel="noopener noreferrer">YES24</a></p><div class="prose">',
    )
  })

  it('keeps the line and leaves the credit out for a book with no source to credit', () => {
    expect(article(written(), '본문', null)).toContain(
      '</h1><p class="byline">AI 학습 콘텐츠 · 책 본문 요약 아님</p>',
    )
  })

  it('says nothing of who wrote a post that is not there to read', () => {
    expect(article(written())).not.toContain('byline')
    expect(article({ kind: 'writing' })).not.toContain('byline')
    expect(article({ kind: 'failed', message: '글을 쓰지 못했어요.', fix: null })).not.toContain(
      'byline',
    )
  })

  it('puts the stale line and 다시 쓰기 above a post of an earlier contents', () => {
    const out = article({ kind: 'written', stale: true }, '본문')
    expect(out).toMatch(
      /<p class="stale">이전 목차로 쓴 글이에요\.<button type="button" class="tbtn">다시 쓰기<\/button><\/p><h1/,
    )
  })

  it('waits with the dot cursor while the post is written', () => {
    const out = article({ kind: 'writing' })
    expect(out).toContain('aria-busy="true"')
    expect(out).toContain('<span class="cur" aria-hidden="true"></span>')
  })

  it('sends a reader without a key to register one, with nothing to retry', () => {
    const out = article({ kind: 'noKey' })
    expect(out).toContain('글을 쓰려면 DeepSeek API 키가 필요해요.')
    expect(out).toContain('<a class="dl" href="/me#api-key-head">마이페이지에서 키 등록하기</a>')
    expect(out).not.toContain('다시 시도')
  })

  it('offers the limit and a retry once the month is spent', () => {
    const out = article({ kind: 'limit' })
    expect(out).toContain('이번 달 한도에 닿아서 이 글을 쓰지 못했어요.')
    expect(out).toContain('<a class="dl" href="/me#usage">마이페이지에서 한도 바꾸기</a>')
    expect(out).toContain('다시 시도')
  })

  it('says why other work failed, with a retry and the setting that fixes it', () => {
    const plain = article({ kind: 'failed', message: '글을 쓰지 못했어요.', fix: null })
    expect(plain).toContain('<p class="fail">글을 쓰지 못했어요.</p>')
    expect(plain).toContain('다시 시도')
    const key = article({ kind: 'failed', message: '키를 확인해 주세요', fix: 'key' })
    expect(key).toContain('href="/me#api-key-head"')
  })

  it('shows only the heading for an item nothing has been asked for yet', () => {
    expect(article({ kind: 'todo' })).toMatch(/<h1 tabindex="-1">.+?<\/h1><\/article>$/)
  })
})
