import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { PageChrome, Pager } from '@/features/reader/components/ReaderChrome'
import { geometryOf, type ItemView, type Layout } from '@/features/reader/utils/reader'
import type { TocItem } from '@/lib/books'

const item = (id: string, parentId: string | null, number: string, title: string): TocItem => ({
  id,
  parentId,
  depth: 0,
  number,
  title,
  itemKey: id,
})
const ITEMS = [item('c1', null, '1', '서론'), item('s11', 'c1', '1.1', '할 일')]
// 1 서론 on 0 | 1.1 on 1-5
const LAYOUT: Layout = { starts: [0, 1], total: 6 }
const GEO = geometryOf(1388, 848, false)
const written = (): ItemView => ({ kind: 'written', stale: false })

describe('PageChrome', () => {
  it('numbers each page in the whole book and heads it with the book, then the chapter or the item', () => {
    const out = renderToStaticMarkup(
      <PageChrome
        geo={GEO}
        layout={LAYOUT}
        first={2}
        book="운영체제"
        items={ITEMS}
        viewOf={written}
      />,
    )
    // the spread 2-3 and one either side: pages 0..5
    expect(out.match(/class="pgc"/g)).toHaveLength(6)
    expect(out).toContain(`left:${2 * GEO.pw}px`)
    expect(out).toMatch(
      /<div class="rh" [^>]*><span class="b">운영체제<\/span><span class="t">1\.1 할 일<\/span><\/div><div class="pn" [^>]*>2</,
    )
    expect(out).toMatch(
      /<div class="rh" [^>]*><span class="b">운영체제<\/span><span class="t">1 서론<\/span><\/div><div class="pn" [^>]*>3</,
    )
  })

  it('marks the running head of an item being written', () => {
    const out = renderToStaticMarkup(
      <PageChrome
        geo={GEO}
        layout={LAYOUT}
        first={2}
        book="운영체제"
        items={ITEMS}
        viewOf={(key) => (key === 's11' ? { kind: 'writing' } : written())}
      />,
    )
    expect(out).toContain('<span class="spin"></span><span>쓰는 중</span>')
  })

  it('heads and numbers the first page too', () => {
    const out = renderToStaticMarkup(
      <PageChrome
        geo={GEO}
        layout={LAYOUT}
        first={0}
        book="운영체제"
        items={ITEMS}
        viewOf={written}
      />,
    )
    expect(out).toMatch(
      /^<div aria-hidden="true"><div class="pgc" style="left:0;width:588px"><div class="rh" [^>]*><span class="b">운영체제<\/span><span class="t">1 서론<\/span><\/div><div class="pn" [^>]*>1</,
    )
  })
})

describe('Pager', () => {
  const pager = (first: number) =>
    renderToStaticMarkup(
      <Pager geo={GEO} layout={LAYOUT} first={first} phone={false} onTurn={() => {}} />,
    )

  it('cannot go back from the first page', () => {
    const out = pager(0)
    expect(out).toContain('<span class="pg-t">1 / 6</span>')
    expect(out).toMatch(/aria-label="이전 쪽" disabled=""/)
  })

  it('reads the left page against the whole book and stops at the last spread', () => {
    const out = pager(4)
    expect(out).toContain('<span class="pg-t">5 / 6</span>')
    expect(out).toMatch(/aria-label="다음 쪽" disabled=""/)
    expect(out).not.toMatch(/aria-label="이전 쪽" disabled=""/)
  })
})
