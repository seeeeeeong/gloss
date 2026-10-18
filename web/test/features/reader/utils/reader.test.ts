import { describe, expect, it } from 'vitest'
import {
  align,
  ancestorsOf,
  anchorAt,
  CONTENTS,
  COVER,
  contentsRowsOf,
  contentsTreeOf,
  currentUnit,
  geometryOf,
  type ItemView,
  itemOfSearch,
  itemsAround,
  itemViewOf,
  type Layout,
  openItemOf,
  pageIn,
  pageOfAnchor,
  pagerLabel,
  readingItemOf,
  rowMarkOf,
  runningHeadOf,
  sameLayout,
  turnsPageFrom,
  unitAt,
  visibleUnits,
} from '@/features/reader/utils/reader'
import type { TocItem } from '@/lib/books'
import type { Generation, PostSummary } from '@/lib/posts'

const item = (
  id: string,
  parentId: string | null,
  number: string | null,
  title: string,
): TocItem => ({ id, parentId, depth: 0, number, title, itemKey: id })

// Part 1 > 1장 > 1.1, 1.2; 2장 > 2.1; and a preface at the top with nothing under it.
const BOOK: TocItem[] = [
  item('pre', null, null, '들어가며'),
  item('p1', null, 'Part 1', '개관'),
  item('c1', 'p1', '1', '서론'),
  item('s11', 'c1', '1.1', '운영체제가 할 일'),
  item('s12', 'c1', '1.2', '컴퓨터 시스템의 구성'),
  item('c2', 'p1', '2', '구조'),
  item('s21', 'c2', '2.1', '서비스'),
]

// cover 0 | 차례 1-3 | item 0 on 4-7 | item 1 on 8 | item 2 on 9-11
const LAYOUT: Layout = { starts: [0, 1, 4, 8, 9], total: 12 }

describe('geometryOf', () => {
  it('lays two pages of the prototype size at 1440 x 900 with the rail', () => {
    const g = geometryOf(1388, 848, false)
    expect([g.cols, g.pw, g.ph, g.tw, g.th]).toEqual([2, 588, 784, 488, 672])
    expect(g.th / 28).toBe(24)
  })

  it('narrows the pages when a right panel takes 380 of the main area', () => {
    const g = geometryOf(1008, 848, false)
    expect([g.cols, g.pw, g.ph, g.tw, g.th]).toEqual([2, 464, 742, 386, 616])
    expect(g.left).toBe(40)
  })

  it('sets one page across a phone, 22 lines under its taller header', () => {
    const g = geometryOf(390, 788, true)
    expect([g.cols, g.pw, g.ph, g.px, g.tw, g.th]).toEqual([1, 390, 720, 24, 342, 616])
  })

  it('falls back to one page where two would be narrower than 300', () => {
    expect(geometryOf(600, 848, false).cols).toBe(1)
  })

  it('keeps a whole number of lines at the line of a larger reading size', () => {
    const g = geometryOf(1388, 848, false, 32)
    expect(g.th % 32).toBe(0)
    expect(geometryOf(1388, 848, false).th % 28).toBe(0)
  })
})

describe('pages and units', () => {
  it('finds the unit a page belongs to and its number in it', () => {
    expect(unitAt(LAYOUT, 0)).toBe(COVER)
    expect(unitAt(LAYOUT, 3)).toBe(CONTENTS)
    expect(pageIn(LAYOUT, 6)).toEqual({ unit: 2, n: 3, of: 4 })
    expect(pageIn(LAYOUT, 11)).toEqual({ unit: 4, n: 3, of: 3 })
  })

  it('opens a spread on its left page, within the book', () => {
    expect(align(5, 2, 12)).toBe(4)
    expect(align(99, 2, 12)).toBe(10)
    expect(align(-3, 2, 12)).toBe(0)
    expect(align(5, 1, 12)).toBe(5)
  })

  it('keeps the reader on the same page of an item when the 차례 grows a page', () => {
    const anchor = anchorAt(LAYOUT, 10)
    expect(anchor).toEqual({ unit: 4, offset: 1 })
    const grown: Layout = { starts: [0, 1, 5, 9, 10], total: 13 }
    expect(pageOfAnchor(grown, anchor)).toBe(11)
  })

  it('stops at the last page of an item that came out shorter', () => {
    const shrunk: Layout = { starts: [0, 1, 4, 8, 9], total: 10 }
    expect(pageOfAnchor(shrunk, { unit: 4, offset: 2 })).toBe(9)
  })

  it('compares layouts by every start', () => {
    expect(sameLayout(null, LAYOUT)).toBe(false)
    expect(sameLayout(LAYOUT, { starts: [0, 1, 4, 8, 9], total: 12 })).toBe(true)
    expect(sameLayout(LAYOUT, { starts: [0, 1, 4, 8, 10], total: 12 })).toBe(false)
  })
})

describe('the open spread', () => {
  it('lists the units on it once each', () => {
    expect(visibleUnits(LAYOUT, 0, 2)).toEqual([COVER, CONTENTS])
    expect(visibleUnits(LAYOUT, 8, 2)).toEqual([3, 4])
    expect(visibleUnits(LAYOUT, 4, 2)).toEqual([2])
    expect(visibleUnits(LAYOUT, 11, 1)).toEqual([4])
  })

  it('names the item the reader went to, else the item beside the 차례, else the left page', () => {
    expect(currentUnit([3, 4], 4)).toBe(4)
    expect(currentUnit([3, 4], null)).toBe(3)
    expect(currentUnit([CONTENTS, 2], null)).toBe(2)
    expect(currentUnit([COVER, CONTENTS], 2)).toBe(COVER)
  })

  it('reads "n / of" for an item and "차례" on the front spreads, by the left page', () => {
    expect(pagerLabel(LAYOUT, 0)).toBe('차례')
    expect(pagerLabel(LAYOUT, 2)).toBe('차례')
    expect(pagerLabel(LAYOUT, 4)).toBe('1 / 4')
    expect(pagerLabel(LAYOUT, 6)).toBe('3 / 4')
    expect(pagerLabel(LAYOUT, 8)).toBe('1 / 1')
  })

  it('gives the item open, or the one gone to before any layout', () => {
    const items = BOOK.slice(0, 3)
    expect(openItemOf({ items, layout: LAYOUT, first: 0, cols: 2, focus: null })).toBeNull()
    expect(openItemOf({ items, layout: LAYOUT, first: 8, cols: 2, focus: 'c1' })).toBe('c1')
    expect(openItemOf({ items, layout: LAYOUT, first: 8, cols: 2, focus: null })).toBe('p1')
    expect(openItemOf({ items, layout: null, first: 0, cols: 2, focus: 'p1' })).toBe('p1')
  })

  it('reads the posts on the spread and one either side', () => {
    expect(itemsAround([COVER, CONTENTS], 7)).toEqual([0])
    expect(itemsAround([3, 4], 7)).toEqual([0, 1, 2, 3])
    expect(itemsAround([8], 7)).toEqual([5, 6])
  })

  it('runs the chapter on the left, the item on the right and "차례" after its first page', () => {
    const items = BOOK.slice(0, 3)
    expect(runningHeadOf(LAYOUT, 0, items)).toBeNull()
    expect(runningHeadOf(LAYOUT, 1, items)).toBeNull()
    expect(runningHeadOf(LAYOUT, 2, items)).toBe('차례')
    expect(runningHeadOf(LAYOUT, 10, items)).toBe('Part 1 개관')
    expect(runningHeadOf(LAYOUT, 11, items)).toBe('1 서론')
    expect(runningHeadOf(LAYOUT, 4, items)).toBe('들어가며')
  })
})

const summary = (stale = false): PostSummary => ({
  itemKey: 'k',
  versionNo: 1,
  stale,
  generatedAt: '2026-10-15T01:52:00Z',
})
const failed = (code: string | null, message: string | null = null): Generation => ({
  state: 'failed',
  code,
  message,
})

describe('itemViewOf', () => {
  it('is written, stale or not, when a post is kept and nothing is asked', () => {
    expect(itemViewOf(summary(), undefined)).toEqual({ kind: 'written', stale: false })
    expect(itemViewOf(summary(true), undefined)).toEqual({ kind: 'written', stale: true })
    expect(itemViewOf(undefined, undefined)).toEqual({ kind: 'todo' })
  })

  it('is being written while the plan or the post is under way, and until a kept post is read', () => {
    for (const state of ['planning', 'writing', 'ready']) {
      const generation = { state, code: null, message: null }
      expect(itemViewOf(summary(true), generation)).toEqual({ kind: 'writing' })
    }
    expect(itemViewOf(undefined, { state: 'none', code: null, message: null })).toEqual({
      kind: 'none',
    })
  })

  it('tells a missing key and a spent month apart from other failures', () => {
    expect(itemViewOf(undefined, failed('llm.no_key', '키를 등록해 주세요'))).toEqual({
      kind: 'noKey',
    })
    expect(itemViewOf(undefined, failed('budget.limit_reached', '한도'))).toEqual({
      kind: 'limit',
    })
  })

  it("shows a known failure in the server's words, an unknown one in a sentence of its own", () => {
    expect(itemViewOf(undefined, failed('llm.key_rejected', '키를 확인해 주세요'))).toEqual({
      kind: 'failed',
      message: '키를 확인해 주세요',
      fix: 'key',
    })
    expect(itemViewOf(undefined, failed('budget.limit_not_set', '한도를 정해 주세요'))).toEqual({
      kind: 'failed',
      message: '한도를 정해 주세요',
      fix: 'usage',
    })
    expect(itemViewOf(undefined, failed('error', '글을 만들지 못했습니다'))).toEqual({
      kind: 'failed',
      message: '글을 쓰지 못했어요.',
      fix: null,
    })
    for (const code of ['post.check_failed', 'post.writing_taken', 'post.unstorable_reply']) {
      expect(itemViewOf(undefined, failed(code, '글을 만들지 못했습니다'))).toEqual({
        kind: 'failed',
        message: '글을 쓰지 못했어요.',
        fix: null,
      })
    }
    expect(itemViewOf(undefined, failed('llm.provider_busy', '바쁩니다'))).toEqual({
      kind: 'failed',
      message: '바쁩니다',
      fix: null,
    })
  })
})

describe('contentsRowsOf', () => {
  it('lists parts and chapters, and the sections only of the chapter being read', () => {
    const rows = contentsRowsOf(BOOK, 's12')
    expect(rows.map((row) => [row.kind, row.label])).toEqual([
      ['chapter', '들어가며'],
      ['part', 'Part 1 개관'],
      ['chapter', '1 서론'],
      ['section', '1.1 운영체제가 할 일'],
      ['section', '1.2 컴퓨터 시스템의 구성'],
      ['chapter', '2 구조'],
    ])
  })

  it('folds a chapter into one row that stands for all its items', () => {
    const rows = contentsRowsOf(BOOK, null)
    expect(rows.find((row) => row.key === 'c1')?.keys).toEqual(['c1', 's11', 's12'])
    expect(rows.some((row) => row.kind === 'section')).toBe(false)
  })

  it('takes the first level for chapters in a book of two levels', () => {
    const flat = [
      item('a', null, '1', '가'),
      item('a1', 'a', '1.1', '나'),
      item('b', null, '2', '다'),
    ]
    const rows = contentsRowsOf(flat, 'a1')
    expect(rows.map((row) => [row.kind, row.key])).toEqual([
      ['chapter', 'a'],
      ['section', 'a1'],
      ['chapter', 'b'],
    ])
  })
})

describe('rowMarkOf', () => {
  const views: Record<string, ItemView> = {
    c1: { kind: 'written', stale: false },
    s11: { kind: 'written', stale: false },
    s12: { kind: 'writing' },
    s21: { kind: 'todo' },
  }
  const viewOf = (key: string): ItemView => views[key] ?? { kind: 'todo' }
  const isRead = (key: string) => key === 's11'
  const rows = contentsRowsOf(BOOK, 's11')
  const markOf = (key: string) => {
    const row = rows.find((r) => r.key === key)
    if (!row) throw new Error(key)
    return rowMarkOf(row, viewOf, isRead)
  }

  it('checks a read item, spins for one being written and quiets one with nothing written', () => {
    expect(markOf('s11')).toEqual({ live: false, todo: false, read: true })
    expect(markOf('s12')).toEqual({ live: true, todo: false, read: false })
    expect(markOf('c2')).toEqual({ live: false, todo: true, read: false })
  })

  it('never checks a folded chapter, which stands for more than one item', () => {
    const folded = contentsRowsOf(BOOK, null).find((row) => row.key === 'c1')
    if (!folded) throw new Error('c1')
    expect(rowMarkOf(folded, viewOf, () => true)).toEqual({ live: true, todo: false, read: false })
  })
})

describe('contentsTreeOf', () => {
  const shape = (nodes: ReturnType<typeof contentsTreeOf>): unknown[] =>
    nodes.map((node) => [node.item.itemKey, node.kind, ...shape(node.children)])

  it('sets parts over chapters over sections in a book three levels deep', () => {
    expect(shape(contentsTreeOf(BOOK))).toEqual([
      ['pre', 'chapter'],
      [
        'p1',
        'part',
        ['c1', 'chapter', ['s11', 'section'], ['s12', 'section']],
        ['c2', 'chapter', ['s21', 'section']],
      ],
    ])
  })

  it('starts at chapters in a shallower book, and keeps each item as the contents has it', () => {
    const flat = [item('c1', null, '1', '서론'), item('s11', 'c1', '1.1', '할 일')]
    const tree = contentsTreeOf(flat)
    expect(shape(tree)).toEqual([['c1', 'chapter', ['s11', 'section']]])
    expect(tree[0]?.item).toBe(flat[0])
  })
})

describe('ancestorsOf', () => {
  it('lists the items an item sits under, outermost first', () => {
    const at = (key: string) => BOOK.find((it) => it.itemKey === key) as TocItem
    expect(ancestorsOf(BOOK, at('s12')).map((up) => up.itemKey)).toEqual(['p1', 'c1'])
    expect(ancestorsOf(BOOK, at('pre'))).toEqual([])
  })
})

describe('itemOfSearch', () => {
  it('reads the item the address names, if the book has it', () => {
    expect(itemOfSearch('?item=s21', BOOK)).toBe('s21')
    expect(itemOfSearch(`?item=${encodeURIComponent('a/b')}`, [item('a/b', null, null, 'x')])).toBe(
      'a/b',
    )
    expect(itemOfSearch('?item=gone', BOOK)).toBeNull()
    expect(itemOfSearch('', BOOK)).toBeNull()
  })
})

describe('readingItemOf', () => {
  const written = (): ItemView => ({ kind: 'written', stale: false })
  const todo = (): ItemView => ({ kind: 'todo' })
  const read = (
    first: number,
    cols: number,
    viewOf: (i: number) => ItemView,
    focus = null as number | null,
  ) => readingItemOf({ layout: LAYOUT, first, cols, focus, viewOf })

  it("is the left page's item while it goes on", () => {
    expect(read(4, 2, written)).toBe(0)
    expect(read(6, 2, todo)).toBe(0)
  })

  it('is the item starting on the right once a written item ends on the left', () => {
    // item 1 is page 8 alone; the unwritten item 2 starts on page 9 and a turn skips to page 10
    const views = (i: number): ItemView => (i === 2 ? todo() : written())
    expect(read(8, 2, views)).toBe(2)
    expect(read(8, 2, (i) => (i === 1 ? { kind: 'none' } : todo()))).toBe(2)
  })

  it('stays on an item still to come that ends on the left, never the one peeking in', () => {
    expect(read(8, 2, todo)).toBe(1)
    expect(read(8, 2, (i) => (i === 1 ? { kind: 'writing' } : todo()))).toBe(1)
  })

  it("is the right page's item when the left is the 차례", () => {
    const layout: Layout = { starts: [0, 1, 3, 5], total: 7 }
    expect(readingItemOf({ layout, first: 2, cols: 2, focus: null, viewOf: written })).toBe(0)
    expect(readingItemOf({ layout, first: 0, cols: 2, focus: null, viewOf: written })).toBeNull()
  })

  it('is the item gone to by name while it is on the spread', () => {
    expect(read(8, 2, todo, 2)).toBe(2)
    expect(read(4, 2, todo, 2)).toBe(0)
  })

  it('is only the page shown on a phone', () => {
    expect(read(3, 1, written)).toBeNull()
    expect(read(8, 1, written)).toBe(1)
  })
})

describe('turnsPageFrom', () => {
  // A stand-in for an element: it sits inside whatever its classes name.
  const at = (...within: string[]) => ({
    closest: (selectors: string) =>
      selectors.split(',').some((s) => within.includes(s.trim())) ? {} : null,
  })

  it('turns the page from the book and its contents, and never from the tutor or its controls', () => {
    expect(turnsPageFrom(at('.flow'))).toBe(true)
    expect(turnsPageFrom(null)).toBe(true)
    expect(turnsPageFrom(at('.tree-col'))).toBe(true)
    for (const control of ['.tg', '.t-fab', '.t-phone', '.tutor', '.ask', 'textarea']) {
      expect(turnsPageFrom(at(control))).toBe(false)
    }
  })
})
