import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { ContentsRow, ItemView, RowMark } from '../lib/reader'
import { Contents, Cover, ItemArticle } from './ReaderPages'

const noop = () => {}
const article = (view: ItemView, markdown: string | null = null) =>
  renderToStaticMarkup(
    <ItemArticle
      unit={3}
      heading="1.1 운영체제가 할 일"
      view={view}
      markdown={markdown}
      onRetry={noop}
    />,
  )

describe('ItemArticle', () => {
  it('shows a written post under its heading, as a unit of the flow', () => {
    const out = article({ kind: 'written', stale: false }, '## 학습 목표\n\n- 하나')
    expect(out).toMatch(/^<article class="item" data-unit="3" aria-busy="false">/)
    expect(out).toContain('<h1 tabindex="-1">1.1 운영체제가 할 일</h1>')
    expect(out).toContain('<h2>학습 목표</h2>')
    expect(out).not.toContain('이전 목차')
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
    expect(article({ kind: 'todo' })).toMatch(/<h1 tabindex="-1">[^<]+<\/h1><\/article>$/)
  })
})

describe('Cover', () => {
  it('names the book, its authors and publisher, and credits the source', () => {
    const book = {
      id: 'b1',
      title: '운영체제 10판',
      authors: ['Abraham Silberschatz', 'Greg Gagne'],
      publisher: '퍼스트북',
      source: 'yes24',
      sourceUrl: 'https://www.yes24.com/product/goods/1',
      tocVersionNo: 2,
    }
    const out = renderToStaticMarkup(<Cover book={book} />)
    expect(out).toContain('data-unit="0"')
    expect(out).toContain('<h1 class="cv-t">운영체제 10판</h1>')
    expect(out).toContain('<span>Abraham Silberschatz, Greg Gagne</span><span>퍼스트북</span>')
    expect(out).toContain('출처 YES24')
    expect(out).toContain(
      '<span>목차 버전 2 · <a class="dl" href="/books/b1/contents">고치기</a></span>',
    )
  })
})

describe('Contents', () => {
  const rows: ContentsRow[] = [
    { key: 'p1', label: 'Part 1 개관', kind: 'part', depth: 0, keys: ['p1'] },
    { key: 'c1', label: '1 서론', kind: 'chapter', depth: 0, keys: ['c1'] },
    { key: 's11', label: '1.1 할 일', kind: 'section', depth: 0, keys: ['s11'] },
    { key: 's12', label: '1.2 구성', kind: 'section', depth: 0, keys: ['s12'] },
    { key: 'c2', label: '2 구조', kind: 'chapter', depth: 0, keys: ['c2', 's21'] },
  ]
  const marks: Record<string, RowMark> = {
    s11: { live: false, todo: false, read: true },
    s12: { live: true, todo: false, read: false },
    c2: { live: false, todo: true, read: false },
  }
  const markOf = (row: ContentsRow) => marks[row.key] ?? { live: false, todo: false, read: false }
  const out = renderToStaticMarkup(<Contents bookId="b1" rows={rows} markOf={markOf} onGo={noop} />)

  it('links every row to the reader at its item', () => {
    expect(out).toContain('data-unit="1"')
    expect(out).toContain('<h1 class="toc-t" id="contents-title">차례</h1>')
    expect(out).toContain('<a class="tc-row tc-part" href="/books/b1?item=p1">')
    expect(out).toContain('href="/books/b1?item=s12"')
  })

  it('checks what was read, spins for what is being written and quiets what has nothing', () => {
    expect(out).toMatch(/1\.1 할 일<\/span><span class="rd" role="img" aria-label="읽음"><svg/)
    expect(out).toMatch(
      /class="tc-row tc-section todo"[^>]*><span class="tl">1\.2 구성<\/span><span class="spin" role="img" aria-label="쓰는 중">/,
    )
    expect(out).toContain('<a class="tc-row tc-chapter todo" href="/books/b1?item=c2">')
  })
})
