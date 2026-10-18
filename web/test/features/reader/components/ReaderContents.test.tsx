import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ContentsTree } from '@/features/reader/components/ReaderContents'
import type { ItemView } from '@/features/reader/utils/reader'
import type { TocItem } from '@/lib/books'

const item = (id: string, parentId: string | null, number: string, title: string): TocItem => ({
  id,
  parentId,
  depth: 0,
  number,
  title,
  itemKey: id,
})
// Part 1 > 1장 > 1.1, 1.2; 2장 > 2.1
const ITEMS = [
  item('p1', null, 'Part 1', '개관'),
  item('c1', 'p1', '1', '서론'),
  item('s11', 'c1', '1.1', '할 일'),
  item('s12', 'c1', '1.2', '구성'),
  item('c2', 'p1', '2', '구조'),
  item('s21', 'c2', '2.1', '서비스'),
]
const VIEWS: Record<string, ItemView> = {
  s11: { kind: 'written', stale: false },
  s12: { kind: 'writing' },
}
const viewOf = (key: string): ItemView => VIEWS[key] ?? { kind: 'todo' }

const tree = (current: string | null) =>
  renderToStaticMarkup(
    <ContentsTree
      bookId="b1"
      items={ITEMS}
      current={current}
      viewOf={viewOf}
      marked={new Set(['s11'])}
      talked={new Set(['s12'])}
      onGo={() => {}}
    />,
  )

describe('ContentsTree', () => {
  it('opens the chapter of the item open and marks the item, leaving the others folded', () => {
    const out = tree('s12')
    expect(out).toContain('<p class="tree-cap"><span>차례</span><span>1 / 6</span></p>')
    expect(out).toMatch(
      /<li class="tr-part"><a class="tr-row d1 todo" href="\/books\/b1\?item=p1">/,
    )
    expect(out).toContain('aria-expanded="true" aria-label="1 서론 접기"')
    expect(out).toContain('aria-expanded="false" aria-label="2 구조 펼치기"')
    expect(out).toMatch(
      /<a class="tr-row d3 todo" href="\/books\/b1\?item=s12" aria-current="page">/,
    )
    expect(out).not.toContain('item=s21')
  })

  it('quiets what is not written, and marks writing, talks and bookmarks', () => {
    const out = tree('s12')
    expect(out).toMatch(
      /<a class="tr-row d3" href="\/books\/b1\?item=s11">.*?<span class="ic-mark" role="img" aria-label="책갈피">/,
    )
    expect(out).toMatch(
      /item=s12".*?<span class="spin" role="img" aria-label="쓰는 중"><\/span><span class="ic-chat" role="img" aria-label="튜터와 나눈 대화">/,
    )
  })

  it('opens a group at each part, its chapters plain and their sections quieter', () => {
    const out = tree('s12')
    expect(out).toMatch(/<a class="tr-row d2 todo" href="\/books\/b1\?item=c1">/)
    expect(out).toMatch(/<a class="tr-row d3" href="\/books\/b1\?item=s11">/)
  })

  it('opens a group at each chapter in a book without parts', () => {
    const flat = ITEMS.slice(1).map((it) => (it.parentId === 'p1' ? { ...it, parentId: null } : it))
    const out = renderToStaticMarkup(
      <ContentsTree
        bookId="b1"
        items={flat}
        current="s11"
        viewOf={viewOf}
        marked={new Set()}
        talked={new Set()}
        onGo={() => {}}
      />,
    )
    expect(out).toMatch(/<a class="tr-row d1 todo" href="\/books\/b1\?item=c1">/)
    expect(out).toMatch(/<a class="tr-row d2" href="\/books\/b1\?item=s11" aria-current="page">/)
  })

  it('keeps every chapter folded on the front pages', () => {
    const out = tree(null)
    expect(out).not.toContain('aria-current')
    expect(out).not.toContain('item=s11')
  })
})
