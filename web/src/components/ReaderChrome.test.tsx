import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import type { TocItem } from '../lib/books'
import { geometryOf, type ItemView, type Layout } from '../lib/reader'
import { PageChrome, Pager } from './ReaderChrome'

const item = (id: string, parentId: string | null, number: string, title: string): TocItem => ({
  id,
  parentId,
  depth: 0,
  number,
  title,
  itemKey: id,
})
const ITEMS = [item('c1', null, '1', '서론'), item('s11', 'c1', '1.1', '할 일')]
// cover 0 | 차례 1-2 | 1 서론 on 3 | 1.1 on 4-7
const LAYOUT: Layout = { starts: [0, 1, 3, 4], total: 8 }
const GEO = geometryOf(1388, 848, false)
const written = (): ItemView => ({ kind: 'written', stale: false })

describe('PageChrome', () => {
  it('numbers each page inside its item and runs chapter left, item right', () => {
    const out = renderToStaticMarkup(
      <PageChrome geo={GEO} layout={LAYOUT} first={4} items={ITEMS} viewOf={written} />,
    )
    // the spread 4-5 and one either side: pages 2..7
    expect(out.match(/class="pgc"/g)).toHaveLength(6)
    expect(out).toContain(`left:${4 * GEO.pw}px`)
    expect(out).toMatch(
      /<div class="rh" [^>]*><span class="t">1 서론<\/span><\/div><div class="pn" [^>]*>1</,
    )
    expect(out).toMatch(
      /<div class="rh r" [^>]*><span class="t">1\.1 할 일<\/span><\/div><div class="pn" [^>]*>2</,
    )
    expect(out).toContain('<span class="t">차례</span>')
  })

  it('marks the running head of an item being written', () => {
    const out = renderToStaticMarkup(
      <PageChrome
        geo={GEO}
        layout={LAYOUT}
        first={4}
        items={ITEMS}
        viewOf={(key) => (key === 's11' ? { kind: 'writing' } : written())}
      />,
    )
    expect(out).toContain('<span class="spin"></span><span>쓰는 중</span>')
  })

  it('gives the cover neither a head nor a number', () => {
    const out = renderToStaticMarkup(
      <PageChrome geo={GEO} layout={LAYOUT} first={0} items={ITEMS} viewOf={written} />,
    )
    expect(out).toMatch(
      /^<div aria-hidden="true"><div class="pgc" style="left:0;width:588px"><\/div>/,
    )
  })
})

describe('Pager', () => {
  const pager = (first: number) =>
    renderToStaticMarkup(
      <Pager geo={GEO} layout={LAYOUT} first={first} phone={false} onTurn={() => {}} />,
    )

  it('reads 차례 on the front and cannot go back from the cover', () => {
    const out = pager(0)
    expect(out).toContain('<span class="pg-t">차례</span>')
    expect(out).toMatch(/aria-label="이전 쪽" disabled=""/)
  })

  it('reads the left page against the item and stops at the last spread', () => {
    const out = pager(6)
    expect(out).toContain('<span class="pg-t">3 / 4</span>')
    expect(out).toMatch(/aria-label="다음 쪽" disabled=""/)
    expect(out).not.toMatch(/aria-label="이전 쪽" disabled=""/)
  })
})
