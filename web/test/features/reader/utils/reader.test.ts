import { describe, expect, it } from 'vitest'
import {
  align,
  ancestorsOf,
  anchorAt,
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
  readableItems,
  readingItemOf,
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

// item 0 on 0-3 | item 1 on 4 | item 2 on 5-7
const LAYOUT: Layout = { starts: [0, 4, 5], total: 8 }

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
  it('finds the item a page belongs to and its number in it', () => {
    expect(unitAt(LAYOUT, 0)).toBe(0)
    expect(unitAt(LAYOUT, 4)).toBe(1)
    expect(pageIn(LAYOUT, 2)).toEqual({ unit: 0, n: 3, of: 4 })
    expect(pageIn(LAYOUT, 7)).toEqual({ unit: 2, n: 3, of: 3 })
  })

  it('opens a spread on its left page, within the book', () => {
    expect(align(5, 2, 8)).toBe(4)
    expect(align(99, 2, 8)).toBe(6)
    expect(align(-3, 2, 8)).toBe(0)
    expect(align(5, 1, 8)).toBe(5)
  })

  it('keeps the reader on the same page of an item when one before it grows a page', () => {
    const anchor = anchorAt(LAYOUT, 6)
    expect(anchor).toEqual({ unit: 2, offset: 1 })
    const grown: Layout = { starts: [0, 5, 6], total: 9 }
    expect(pageOfAnchor(grown, anchor)).toBe(7)
  })

  it('stops at the last page of an item that came out shorter', () => {
    const shrunk: Layout = { starts: [0, 4, 5], total: 6 }
    expect(pageOfAnchor(shrunk, { unit: 2, offset: 2 })).toBe(5)
  })

  it('compares layouts by every start', () => {
    expect(sameLayout(null, LAYOUT)).toBe(false)
    expect(sameLayout(LAYOUT, { starts: [0, 4, 5], total: 8 })).toBe(true)
    expect(sameLayout(LAYOUT, { starts: [0, 4, 6], total: 8 })).toBe(false)
  })
})

describe('the open spread', () => {
  it('lists the items on it once each', () => {
    expect(visibleUnits(LAYOUT, 0, 2)).toEqual([0])
    expect(visibleUnits(LAYOUT, 4, 2)).toEqual([1, 2])
    expect(visibleUnits(LAYOUT, 7, 1)).toEqual([2])
  })

  it('names the item the reader went to while it shows, else the left page', () => {
    expect(currentUnit([1, 2], 2)).toBe(2)
    expect(currentUnit([1, 2], null)).toBe(1)
    expect(currentUnit([1, 2], 0)).toBe(1)
  })

  it('reads the left page against the whole book', () => {
    expect(pagerLabel(LAYOUT, 0)).toBe('1 / 8')
    expect(pagerLabel(LAYOUT, 4)).toBe('5 / 8')
    expect(pagerLabel({ starts: [0, 4, 9], total: 12 }, 4)).toBe('5 / 12')
  })

  it('gives the item open, or the one gone to before any layout, else the first', () => {
    const items = BOOK.slice(0, 3)
    expect(openItemOf({ items, layout: LAYOUT, first: 0, cols: 2, focus: null })).toBe('pre')
    expect(openItemOf({ items, layout: LAYOUT, first: 4, cols: 2, focus: 'c1' })).toBe('c1')
    expect(openItemOf({ items, layout: LAYOUT, first: 4, cols: 2, focus: null })).toBe('p1')
    expect(openItemOf({ items, layout: null, first: 0, cols: 2, focus: 'p1' })).toBe('p1')
    expect(openItemOf({ items, layout: null, first: 0, cols: 2, focus: null })).toBe('pre')
  })

  it('reads the posts on the spread and one either side', () => {
    expect(itemsAround([0], 7)).toEqual([0, 1])
    expect(itemsAround([1, 2], 7)).toEqual([0, 1, 2, 3])
    expect(itemsAround([6], 7)).toEqual([5, 6])
  })

  it('runs the chapter on the left page and the item on the right', () => {
    const items = BOOK.slice(0, 3)
    expect(runningHeadOf(LAYOUT, 0, items)).toBe('들어가며')
    expect(runningHeadOf(LAYOUT, 6, items)).toBe('Part 1 개관')
    expect(runningHeadOf(LAYOUT, 7, items)).toBe('1 서론')
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

describe('readableItems', () => {
  it('leaves out the unnumbered items before the first numbered one and after the last, with what is under them', () => {
    const book = [
      item('rec', null, null, '추천사'),
      item('pre', null, null, '들어가며'),
      item('pre1', 'pre', null, '이 책의 구성'),
      item('c1', null, '1', '서론'),
      item('s11', 'c1', '1.1', '할 일'),
      item('end', null, null, '마치며'),
    ]
    expect(readableItems(book).map((it) => it.itemKey)).toEqual(['c1', 's11'])
  })

  it('leaves out the unnumbered items after the last numbered one, keeping the sections in it', () => {
    const book = [
      item('c1', null, '1', '서론'),
      item('s11', 'c1', null, '정리'),
      item('words', null, null, '용어집'),
      item('notes', null, null, '미주'),
      item('index', null, null, '찾아보기'),
    ]
    expect(readableItems(book).map((it) => it.itemKey)).toEqual(['c1', 's11'])
  })

  it('keeps an unnumbered part that holds the first numbered chapter', () => {
    expect(readableItems(BOOK).map((it) => it.itemKey)).toEqual([
      'p1',
      'c1',
      's11',
      's12',
      'c2',
      's21',
    ])
  })

  it('keeps every item of a book that numbers nothing, or opens on a numbered one', () => {
    const plain = [item('a', null, null, '하나'), item('b', null, null, '둘')]
    expect(readableItems(plain)).toBe(plain)
    const numbered = BOOK.slice(1)
    expect(readableItems(numbered)).toBe(numbered)
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
    expect(read(0, 2, written)).toBe(0)
    expect(read(2, 2, todo)).toBe(0)
  })

  it('is the item starting on the right once a written item ends on the left', () => {
    // item 1 is page 4 alone; the unwritten item 2 starts on page 5 and a turn skips to page 6
    const views = (i: number): ItemView => (i === 2 ? todo() : written())
    expect(read(4, 2, views)).toBe(2)
    expect(read(4, 2, (i) => (i === 1 ? { kind: 'none' } : todo()))).toBe(2)
  })

  it('stays on an item still to come that ends on the left, never the one peeking in', () => {
    expect(read(4, 2, todo)).toBe(1)
    expect(read(4, 2, (i) => (i === 1 ? { kind: 'writing' } : todo()))).toBe(1)
  })

  it('is the item gone to by name while it is on the spread', () => {
    expect(read(4, 2, todo, 2)).toBe(2)
    expect(read(0, 2, todo, 2)).toBe(0)
  })

  it('is only the page shown on a phone', () => {
    expect(read(3, 1, written)).toBe(0)
    expect(read(4, 1, written)).toBe(1)
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
