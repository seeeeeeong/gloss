import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { HeldContents } from '../lib/setup'
import { ContentsPanel, SetupContents, type ShownContents } from './SetupContents'

const noop = () => {}

const held: HeldContents = {
  ref: '101',
  source: 'yes24',
  sourceUrl: 'https://www.yes24.com/product/goods/101',
  title: '운영체제',
  authors: ['Silberschatz'],
  book: {
    ref: '101',
    source: 'yes24',
    url: 'https://www.yes24.com/product/goods/101',
    title: '운영체제',
    authors: ['Silberschatz'],
    publisher: '퍼스트북',
    publishedOn: '2020-02-28',
    kind: '도서',
    cover: 'https://image.yes24.com/goods/101/L',
    hasContents: true,
  },
  found: null,
  raw: '1 서론\n  1.1 운영체제가 할 일',
  items: [
    { ord: 0, parent: null, depth: 0, number: '1', title: '서론' },
    { ord: 1, parent: 0, depth: 1, number: '1.1', title: '운영체제가 할 일' },
  ],
}

const contents = (more: Partial<ShownContents> = {}): ShownContents => ({
  seq: 5,
  held,
  live: true,
  reviewing: false,
  waiting: false,
  starting: null,
  searching: false,
  notice: null,
  onReview: noop,
  onSeek: noop,
  onStart: noop,
  ...more,
})

const render = (shown: ShownContents, typed = false) =>
  renderToStaticMarkup(<SetupContents contents={shown} typed={typed} />)

const panel = (shown: ShownContents) =>
  renderToStaticMarkup(
    <ContentsPanel contents={shown} onClose={noop} onTalk={noop} onChoose={noop} />,
  )

describe('SetupContents', () => {
  it('brings the contents as a card that opens them in the panel, then asks how posts are written', () => {
    const out = render(contents())

    expect(out).toContain(
      '<p>목차를 가져왔어요. 살펴보시고 괜찮으면 문체를 골라 시작해 주세요.</p>',
    )
    expect(out).toMatch(
      /<button type="button" id="contents-card" class="tcard" aria-expanded="false">.*?<span class="tcard-title">운영체제<\/span><span class="tcard-meta">2개 항목<\/span>/,
    )
    expect(out).toContain('<p>글은 어떤 문체로 쓸까요?</p>')
    expect(out.match(/이 문체로 시작<\/button>/g)).toHaveLength(3)
    expect(out).not.toContain('운영체제가 할 일')
  })

  it('marks the card while its panel is open', () => {
    expect(render(contents({ reviewing: true }))).toContain('class="tcard" aria-expanded="true"')
  })

  it('shows contents started with, or brought before, as a card with nothing to press', () => {
    const out = render(contents({ live: false }))

    expect(out).toContain('<div class="tcard">')
    expect(out).not.toContain('contents-card')
    expect(out).not.toContain('radio')
    expect(out).toContain('<span class="v-l">교과서체</span>')
  })

  it('says why a start did not go through as a plain sentence, and holds the voices while one starts', () => {
    const out = render(contents({ starting: 'note', notice: '이미 시작한 목차예요.' }))

    expect(out).toContain('<p role="alert">이미 시작한 목차예요.</p>')
    expect(out).toContain('aria-disabled="true" aria-busy="true">시작하는 중</button>')
    expect(out.match(/aria-disabled="true" aria-busy="false">이 문체로 시작/g)).toHaveLength(2)
  })

  it('holds the voices while a message or another pick is on its way', () => {
    const out = render(contents({ waiting: true }))

    expect(out.match(/aria-disabled="true" aria-busy="false">이 문체로 시작/g)).toHaveLength(3)
  })
})

describe('ContentsPanel', () => {
  it('shows the book picked with its cover and facts, every item numbered, and the way on', () => {
    const out = panel(contents())

    expect(out).toContain('<h2 class="p-title" id="panel-title">목차 확인</h2>')
    expect(out).toContain('<div class="p-cover"><img src="https://image.yes24.com/goods/101/L"')
    expect(out).toContain('<span class="p-label">선택한 책</span>')
    expect(out).toContain(
      '<p class="p-book-title"><a class="dl" href="https://www.yes24.com/product/goods/101"',
    )
    expect(out).toContain('<li>저자: Silberschatz</li><li>판본: 퍼스트북 2020</li>')
    expect(out).toContain('<span>출처 YES24</span>')
    expect(out).toContain('<h3 class="p-sub">목차<span>2</span></h3>')
    expect(out).toContain('<li class="d0"><span class="no">1</span><span>서론</span></li>')
    expect(out).toContain(
      '<li class="d1"><span class="no">1.1</span><span>운영체제가 할 일</span></li>',
    )
    expect(out).toMatch(/<button type="button" class="btn sec">대화 더 이어가기<\/button>/)
    expect(out).toMatch(/<button type="button" class="btn primary">이 목차로 시작/)
    expect(out).not.toContain('수정')
  })

  it('offers another source for an edition, held while a look or a message is on its way', () => {
    expect(panel(contents())).toContain(
      '<button type="button" class="tbtn" aria-disabled="false" aria-busy="false">다른 출처 찾기</button>',
    )
    expect(panel(contents({ searching: true, waiting: true }))).toContain(
      'aria-disabled="true" aria-busy="true">다른 출처 찾기</button>',
    )
    expect(panel(contents({ waiting: true }))).toContain(
      'aria-disabled="true" aria-busy="false">다른 출처 찾기</button>',
    )
  })

  it('names the page contents were found on, its host, and still credits the bookshop', () => {
    const found = { url: 'https://github.com/notes/os-toc', title: 'operating-system-concepts' }
    const out = panel(contents({ held: { ...held, found } }))

    expect(out).toContain(
      '<a class="dl found" href="https://github.com/notes/os-toc" target="_blank" rel="noopener noreferrer" title="operating-system-concepts"><span>operating-system-concepts</span>',
    )
    expect(out).toContain('>github.com</a>')
    expect(out).toContain('<span>출처 YES24</span>')
  })

  it('draws a jacket for a book with no cover, with no link, credit or other source', () => {
    const out = panel(
      contents({ held: { ...held, ref: null, source: null, sourceUrl: null, book: null } }),
    )

    expect(out).toMatch(/<div class="p-cover"><div class="jacket tone-/)
    expect(out).toContain('<p class="p-book-title">운영체제</p>')
    expect(out).not.toContain('YES24')
    expect(out).not.toContain('다른 출처 찾기')
  })
})
