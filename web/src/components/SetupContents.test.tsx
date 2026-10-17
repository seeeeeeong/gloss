import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { HeldContents } from '../lib/setup'
import { SetupContents, type ShownContents } from './SetupContents'

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
  editing: false,
  waiting: false,
  starting: null,
  searching: false,
  notice: null,
  onEdit: noop,
  onSeek: noop,
  onStart: noop,
  ...more,
})

const render = (shown: ShownContents, typed = false) =>
  renderToStaticMarkup(<SetupContents contents={shown} typed={typed} />)

describe('SetupContents', () => {
  it('shows the contents as a document under the book, with 수정, then asks how posts are written', () => {
    const out = render(contents())

    expect(out).toContain('<h1>운영체제 전체 목차</h1>')
    expect(out).toContain('<div class="tb-cover"><img src="https://image.yes24.com/goods/101/L"')
    expect(out).toContain(
      '<p class="tb-title"><a class="dl" href="https://www.yes24.com/product/goods/101"',
    )
    expect(out).toContain('<li>저자: Silberschatz</li><li>판본: 퍼스트북 2020</li>')
    expect(out).toContain('<li>구성: 2개 항목</li>')
    expect(out).toContain('<span>출처 YES24</span>')
    expect(out).toContain('id="contents-edit" class="tbtn" aria-expanded="false"')
    expect(out).toContain(
      '<p>목차를 가져왔어요. 살펴보시고 괜찮으면 문체를 골라 시작해 주세요.</p>',
    )
    expect(out).toContain('<h4>1 서론</h4><ul><li>1.1 운영체제가 할 일</li></ul>')
    expect(out).toContain('</div><p>글은 어떤 문체로 쓸까요?</p>')
    expect(out.match(/이 문체로 시작<\/button>/g)).toHaveLength(3)
  })

  it('offers another source next to 수정 for an edition', () => {
    const out = render(contents())

    expect(out).toContain(
      '<button type="button" class="tbtn" aria-disabled="false" aria-busy="false">다른 출처 찾기</button>',
    )
  })

  it('names the page contents were found on, its host, and still credits the bookshop', () => {
    const found = { url: 'https://github.com/notes/os-toc', title: 'operating-system-concepts' }
    const out = render(contents({ held: { ...held, found } }))

    expect(out).toContain(
      '<a class="dl found" href="https://github.com/notes/os-toc" target="_blank" rel="noopener noreferrer" title="operating-system-concepts"><span>operating-system-concepts</span>',
    )
    expect(out).toContain('<a class="spill" href="https://github.com/notes/os-toc"')
    expect(out).toContain('>github.com</a>')
    expect(out).toContain('<span>출처 YES24</span>')
    expect(out).toContain('<p>다른 출처에서 목차를 찾았어요.')
  })

  it('holds every action while detailed contents are looked for', () => {
    // A look keeps the session waiting, as a message does.
    const out = render(contents({ searching: true, waiting: true }))

    expect(out).toContain(
      'id="contents-edit" class="tbtn" aria-expanded="false" aria-disabled="true"',
    )
    expect(out).toContain('aria-disabled="true" aria-busy="true">다른 출처 찾기</button>')
    expect(out.match(/aria-disabled="true" aria-busy="false">이 문체로 시작/g)).toHaveLength(3)
  })

  it('holds 다른 출처 찾기 while a message or a pick is on its way', () => {
    const out = render(contents({ waiting: true }))

    expect(out).toContain('aria-disabled="true" aria-busy="false">다른 출처 찾기</button>')
  })

  it('reads contents started with, or brought before, without their actions', () => {
    const out = render(contents({ live: false }))

    expect(out).toContain('<span>출처 YES24</span>')
    expect(out).not.toContain('수정')
    expect(out).not.toContain('</button>')
    expect(out).not.toContain('radio')
    expect(out).toContain('<span class="v-l">교과서체</span>')
  })

  it('says why a start did not go through as a plain sentence, and holds the voices while one starts', () => {
    const out = render(contents({ starting: 'note', notice: '이미 시작한 목차예요.' }))

    expect(out).toContain('<p role="alert">이미 시작한 목차예요.</p>')
    expect(out).toContain('aria-disabled="true" aria-busy="true">시작하는 중</button>')
    expect(out.match(/aria-disabled="true" aria-busy="false">이 문체로 시작/g)).toHaveLength(2)
    expect(out).not.toContain('f-err')
  })

  it('holds the voices while a message or another pick is on its way', () => {
    const out = render(contents({ waiting: true }))

    expect(out.match(/aria-disabled="true" aria-busy="false">이 문체로 시작/g)).toHaveLength(3)
  })

  it('shows a book typed in without a cover, a link or a credit', () => {
    const out = render(
      contents({ held: { ...held, ref: null, source: null, sourceUrl: null, book: null } }),
      true,
    )

    expect(out).not.toContain('tb-cover')
    expect(out).toContain('<p class="tb-title">운영체제</p>')
    expect(out).not.toContain('YES24')
    expect(out).not.toContain('다른 출처 찾기')
    expect(out).toContain('<p>목차를 받았어요.')
  })
})
