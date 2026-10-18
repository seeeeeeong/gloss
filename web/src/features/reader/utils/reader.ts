import { type TocItem, type TocNode, treeOf } from '@/lib/books'
import { type Generation, headingOf, type PostSummary } from '@/lib/posts'

// Where the page keys belong to what has focus: typing, the app's sidebar, and the tutor's own
// controls, its toggle, floating button and sheet included. The book's contents are links, which
// leave the keys to the pages.
const NOT_FOR_TURNING =
  'input, textarea, select, [contenteditable="true"], .sb, dialog, .tutor, .ask, .tg, .t-fab, .t-phone'

/** Whether a page key pressed with focus on [target] turns the page. */
export function turnsPageFrom(target: { closest: (selectors: string) => unknown } | null): boolean {
  return !target?.closest(NOT_FOR_TURNING)
}

/** A page's text height is a whole number of lines: 28px at the smallest reading size. */
export const LINE = 28

/**
 * Where the pages sit in the stage: [cols] pages of [pw] x [ph] from ([left], [top]), text [tw]
 * wide and [th] tall from [px] in and [pt] down, the running header and page number [hd] from the
 * edge.
 */
export type Geometry = {
  cols: 1 | 2
  pw: number
  ph: number
  px: number
  pt: number
  th: number
  tw: number
  left: number
  top: number
  hd: number
}

const lines = (height: number, line: number) => Math.max(1, Math.floor(height / line)) * line

/** The prototype's geometry: two pages of at most 600 when they fit, one on a phone. */
export function geometryOf(width: number, height: number, phone: boolean, line = LINE): Geometry {
  if (phone) {
    const ph = Math.max(320, height - 68)
    const px = 24
    const pt = 44
    return {
      cols: 1,
      pw: width,
      ph,
      px,
      pt,
      th: lines(ph - pt - 44, line),
      tw: width - 2 * px,
      left: 0,
      top: 0,
      hd: 14,
    }
  }
  // 40px edge zones each side; the pager's 48 under the book.
  const availW = width - 80
  const availH = height - 64
  const two = availW / 2 >= 300
  const cols = two ? 2 : 1
  const pw = Math.floor(Math.min(two ? availW / 2 : availW, availH * 0.75, two ? 600 : 640))
  const ph = Math.floor(Math.min(availH, pw * 1.6))
  const px = Math.max(28, Math.min(52, Math.round(pw * 0.085)))
  const pt = 56
  return {
    cols,
    pw,
    ph,
    px,
    pt,
    th: lines(ph - pt - 56, line),
    tw: pw - 2 * px,
    left: Math.round((width - cols * pw) / 2),
    top: Math.max(8, Math.round((height - ph - 48) / 2)),
    hd: 20,
  }
}

/**
 * The flow's units are the book's items in reading order, so a unit is an item's index; [starts]
 * holds the page each begins on and [total] the pages of the whole flow.
 */
export type Layout = { starts: number[]; total: number }

export function sameLayout(a: Layout | null, b: Layout): boolean {
  return (
    a !== null &&
    a.total === b.total &&
    a.starts.length === b.starts.length &&
    a.starts.every((start, i) => start === b.starts[i])
  )
}

/** The unit a page belongs to: the last one starting on or before it. */
export function unitAt(layout: Layout, page: number): number {
  let unit = 0
  layout.starts.forEach((start, i) => {
    if (start <= page) unit = i
  })
  return unit
}

const startOf = (layout: Layout, unit: number) => layout.starts[unit] ?? 0
const endOf = (layout: Layout, unit: number) => layout.starts[unit + 1] ?? layout.total

/** A page's number inside its unit, and how many pages the unit has. */
export function pageIn(layout: Layout, page: number): { unit: number; n: number; of: number } {
  const unit = unitAt(layout, page)
  const start = startOf(layout, unit)
  return { unit, n: page - start + 1, of: endOf(layout, unit) - start }
}

/** The first page of the spread that shows [page]. */
export function align(page: number, cols: number, total: number): number {
  const p = Math.max(0, Math.min(total - 1, page))
  return p - (p % cols)
}

/**
 * A place in the book that survives a new layout: a unit and a page inside it. A resized
 * viewport or an item that is written moves every page after it, never a page inside its unit.
 */
export type Anchor = { unit: number; offset: number }

export function anchorAt(layout: Layout, page: number): Anchor {
  const unit = unitAt(layout, page)
  return { unit, offset: page - startOf(layout, unit) }
}

export function pageOfAnchor(layout: Layout, anchor: Anchor): number {
  const start = startOf(layout, anchor.unit)
  return Math.min(start + anchor.offset, Math.max(start, endOf(layout, anchor.unit) - 1))
}

/** The units on the spread from [first], in order. */
export function visibleUnits(layout: Layout, first: number, cols: number): number[] {
  const units: number[] = []
  for (let page = first; page < Math.min(first + cols, layout.total); page++) {
    const unit = unitAt(layout, page)
    if (!units.includes(unit)) units.push(unit)
  }
  return units
}

/** The item the header names: the one the reader went to while it shows, else the left page's. */
export function currentUnit(visible: number[], focus: number | null): number {
  if (focus !== null && visible.includes(focus)) return focus
  return visible[0] ?? 0
}

/** "12 / 248": the left page in the whole book, which grows as its posts are written. */
export function pagerLabel(layout: Layout, first: number): string {
  return `${first + 1} / ${layout.total}`
}

/** What an item shows, from its post and from the work this page has asked for. */
export type ItemView =
  | { kind: 'todo' }
  | { kind: 'writing' }
  | { kind: 'none' }
  | { kind: 'written'; stale: boolean }
  | { kind: 'noKey' }
  | { kind: 'limit' }
  | { kind: 'failed'; message: string; fix: 'key' | 'usage' | null }

const FAILED = '글을 쓰지 못했어요.'
const KEY_CODES = ['llm.key_rejected', 'llm.key_unreadable']
// A post that failed its check or could not be kept: the server's words would only say "try again".
const GENERIC_CODES = ['error', 'post.check_failed', 'post.writing_taken', 'post.unstorable_reply']

function failureOf(generation: Generation): ItemView {
  const { code, message } = generation
  if (code === 'llm.no_key') return { kind: 'noKey' }
  if (code === 'budget.limit_reached') return { kind: 'limit' }
  // A job that stopped without a known error says so in its own words; the server's are its fallback.
  const known = code !== null && !GENERIC_CODES.includes(code) && message !== null
  const fix = KEY_CODES.includes(code ?? '')
    ? 'key'
    : code === 'budget.limit_not_set'
      ? 'usage'
      : null
  return { kind: 'failed', message: known ? message : FAILED, fix }
}

/** Work this page asked for wins over the post it had: a rewrite clears the stale one. */
export function itemViewOf(
  summary: PostSummary | undefined,
  generation: Generation | undefined,
): ItemView {
  if (generation) {
    if (generation.state === 'failed') return failureOf(generation)
    if (generation.state === 'none') return { kind: 'none' }
    // planning, writing, and ready until the post itself is read
    return { kind: 'writing' }
  }
  if (summary) return { kind: 'written', stale: summary.stale }
  return { kind: 'todo' }
}

const height = (nodes: TocNode[]): number =>
  nodes.reduce((most, node) => Math.max(most, 1 + height(node.children)), 0)

/** An item in the contents sidebar, with the items under it. */
export type ContentsNode = {
  item: TocItem
  kind: 'part' | 'chapter' | 'section'
  children: ContentsNode[]
}

/** The whole contents as a tree: parts over chapters over sections; a chapter is the second level of a deeper book, else the first. */
export function contentsTreeOf(items: TocItem[]): ContentsNode[] {
  const roots = treeOf(items)
  const chapterLevel = height(roots) >= 3 ? 1 : 0
  const byId = new Map(items.map((item) => [item.id, item]))
  const walk = (nodes: TocNode[], level: number): ContentsNode[] =>
    nodes.map((node) => {
      const part = level < chapterLevel && node.children.length > 0
      return {
        item: byId.get(node.id) ?? node,
        kind: part ? 'part' : level <= chapterLevel ? 'chapter' : 'section',
        children: walk(node.children, level + 1),
      }
    })
  return walk(roots, 0)
}

/** The items [item] sits under, outermost first. */
export function ancestorsOf(items: TocItem[], item: TocItem): TocItem[] {
  const byId = new Map(items.map((other) => [other.id, other]))
  const path: TocItem[] = []
  let up = item.parentId === null ? undefined : byId.get(item.parentId)
  while (up && path.length < items.length) {
    path.unshift(up)
    up = up.parentId === null ? undefined : byId.get(up.parentId)
  }
  return path
}

/** The item an address's query names, if the book has it. */
export function itemOfSearch(search: string, items: TocItem[]): string | null {
  const key = new URLSearchParams(search).get('item')
  return items.find((item) => item.itemKey === key)?.itemKey ?? null
}

/**
 * The items a book is read in. Front and back matter, the unnumbered top-level items before the
 * first one that holds a number (추천사, 들어가며) and after the last (용어집, 찾아보기), are left
 * out; a book that numbers nothing keeps them all.
 */
export function readableItems(items: TocItem[]): TocItem[] {
  const byId = new Map(items.map((item) => [item.id, item]))
  const rootOf = (item: TocItem): string => {
    let at = item
    for (let up = 0; up < items.length; up++) {
      const parent = at.parentId === null ? undefined : byId.get(at.parentId)
      if (!parent) break
      at = parent
    }
    return at.id
  }
  const roots = items.filter((item) => rootOf(item) === item.id).map((item) => item.id)
  const numbered = new Set(items.filter((item) => item.number).map(rootOf))
  const first = roots.findIndex((root) => numbered.has(root))
  const last = roots.findLastIndex((root) => numbered.has(root))
  if (first === -1 || (first === 0 && last === roots.length - 1)) return items
  const kept = new Set(roots.slice(first, last + 1))
  return items.filter((item) => kept.has(rootOf(item)))
}

/** The running header of an item's left page: the item it sits under, or its own. */
export function chapterOf(items: TocItem[], item: TocItem): string {
  const parent = items.find((other) => other.id === item.parentId)
  return headingOf(parent ?? item)
}

type ReadingArgs = {
  layout: Layout
  first: number
  cols: number
  /** The item the reader went to by name; it is the one read while it is on the spread. */
  focus: number | null
  viewOf: (index: number) => ItemView
}

/**
 * The item the reader is reading on the spread from [first], by index: the one gone to by name
 * while it shows, else the left page's. When the left page is the last of an item written or with
 * nothing to write, the item starting on the right is read: a turn moves two pages, so it may never
 * sit on the left. Peeking past an item still to come is not.
 */
export function readingItemOf({ layout, first, cols, focus, viewOf }: ReadingArgs): number {
  if (focus !== null && visibleUnits(layout, first, cols).includes(focus)) return focus
  const left = pageIn(layout, first)
  const done = left.n === left.of && ['written', 'none'].includes(viewOf(left.unit).kind)
  if (!done || cols < 2 || first + 1 >= layout.total) return left.unit
  return unitAt(layout, first + 1)
}

/** The items on a spread and one either side, whose posts are read before a turn reaches them. */
export function itemsAround(visible: number[], count: number): number[] {
  if (visible.length === 0) return []
  const from = Math.max(0, Math.min(...visible) - 1)
  const to = Math.min(count - 1, Math.max(...visible) + 1)
  return Array.from({ length: Math.max(0, to - from + 1) }, (_, i) => from + i)
}

/** A page's running header: an item's chapter on the left page, its own heading on the right. */
export function runningHeadOf(layout: Layout, page: number, items: TocItem[]): string | null {
  const item = items[pageIn(layout, page).unit]
  if (!item) return null
  return page % 2 === 1 ? headingOf(item) : chapterOf(items, item)
}

type OpenArgs = {
  items: TocItem[]
  layout: Layout | null
  first: number
  cols: number
  /** The item the reader last went to; the header keeps it while it is on the spread. */
  focus: string | null
}

/** The item the header names; before the first layout, the one gone to, else the first. */
export function openItemOf({ items, layout, first, cols, focus }: OpenArgs): string | null {
  const at = focus === null ? -1 : items.findIndex((item) => item.itemKey === focus)
  const focusUnit = at < 0 ? null : at
  const visible = layout ? visibleUnits(layout, first, cols) : []
  const unit = visible.length > 0 ? currentUnit(visible, focusUnit) : (focusUnit ?? 0)
  return items[unit]?.itemKey ?? null
}
