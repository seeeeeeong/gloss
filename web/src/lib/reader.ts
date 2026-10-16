import { type TocItem, type TocNode, treeOf } from './books'
import { type Generation, headingOf, type PostSummary } from './posts'

// Where the page keys belong to what has focus: typing, the sidebar and contents, and the tutor's
// own controls, its toggle, floating button and sheet included, all of which sit outside the book.
const NOT_FOR_TURNING =
  'input, textarea, select, [contenteditable="true"], .sb, .tocpop, dialog, .tutor, .ask, .tg, .t-fab, .t-phone'

/** Whether a page key pressed with focus on [target] turns the page. */
export function turnsPageFrom(target: { closest: (selectors: string) => unknown } | null): boolean {
  return !target?.closest(NOT_FOR_TURNING)
}

/** Every line of a page is 28px, so a page's text height is a whole number of lines. */
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

const lines = (height: number) => Math.max(1, Math.floor(height / LINE)) * LINE

/** The prototype's geometry: two pages of at most 600 when they fit, one on a phone. */
export function geometryOf(width: number, height: number, phone: boolean): Geometry {
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
      th: lines(ph - pt - 44),
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
    th: lines(ph - pt - 56),
    tw: pw - 2 * px,
    left: Math.round((width - cols * pw) / 2),
    top: Math.max(8, Math.round((height - ph - 48) / 2)),
    hd: 20,
  }
}

/**
 * The flow's units are the cover, the 차례, then every item in reading order; [starts] holds the
 * page each begins on and [total] the pages of the whole flow.
 */
export type Layout = { starts: number[]; total: number }

export const COVER = 0
export const CONTENTS = 1
export const FIRST_ITEM = 2

export const unitOfItem = (index: number) => FIRST_ITEM + index
export const itemOfUnit = (unit: number) => unit - FIRST_ITEM
export const isItemUnit = (unit: number) => unit >= FIRST_ITEM

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

/** The unit the header names: the one the reader went to, else the spread's item, else its left page's. */
export function currentUnit(visible: number[], focus: number | null): number {
  if (focus !== null && visible.includes(focus)) return focus
  const [left = COVER, right] = visible
  if (!isItemUnit(left) && right !== undefined && isItemUnit(right)) return right
  return left
}

/** "2 / 4" for the left page of an item, "차례" on the cover and 차례 spreads. */
export function pagerLabel(layout: Layout, first: number): string {
  const { unit, n, of } = pageIn(layout, first)
  return isItemUnit(unit) ? `${n} / ${of}` : '차례'
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

export const isWritten = (view: ItemView) => view.kind === 'written'

/** A row of the 차례 page; [keys] are the items it stands for, more than one for a folded chapter. */
export type ContentsRow = {
  key: string
  label: string
  kind: 'part' | 'chapter' | 'section'
  depth: number
  keys: string[]
}

const height = (nodes: TocNode[]): number =>
  nodes.reduce((most, node) => Math.max(most, 1 + height(node.children)), 0)

const keysUnder = (node: TocNode): string[] => [node.itemKey, ...node.children.flatMap(keysUnder)]

/**
 * Parts and chapters, and the sections only under the chapter holding [openKey], so the 차례
 * stays a few pages long. A chapter is the second level of a deeper book, else the first.
 */
export function contentsRowsOf(items: TocItem[], openKey: string | null): ContentsRow[] {
  const roots = treeOf(items)
  const chapterLevel = height(roots) >= 3 ? 1 : 0
  const row = (node: TocNode, kind: ContentsRow['kind'], depth: number, keys: string[]) => ({
    key: node.itemKey,
    label: headingOf(node),
    kind,
    depth,
    keys,
  })
  const sections = (nodes: TocNode[], depth: number): ContentsRow[] =>
    nodes.flatMap((node) => [
      row(node, 'section', depth, [node.itemKey]),
      ...sections(node.children, depth + 1),
    ])
  const chapter = (node: TocNode): ContentsRow[] => {
    const all = keysUnder(node)
    if (openKey === null || !all.includes(openKey)) return [row(node, 'chapter', 0, all)]
    return [row(node, 'chapter', 0, [node.itemKey]), ...sections(node.children, 0)]
  }
  const walk = (nodes: TocNode[], level: number): ContentsRow[] =>
    nodes.flatMap((node) =>
      level < chapterLevel && node.children.length > 0
        ? [row(node, 'part', 0, [node.itemKey]), ...walk(node.children, level + 1)]
        : chapter(node),
    )
  return walk(roots, 0)
}

/** A row of the contents popover: an item with items under it reads as their heading. */
export type MenuRow = { key: string; label: string; heading: boolean }

export function menuRowsOf(items: TocItem[]): MenuRow[] {
  const parents = new Set(items.map((item) => item.parentId))
  return items.map((item) => ({
    key: item.itemKey,
    label: headingOf(item),
    heading: parents.has(item.id),
  }))
}

/** The running header of an item's left page: the item it sits under, or its own. */
export function chapterOf(items: TocItem[], item: TocItem): string {
  const parent = items.find((other) => other.id === item.parentId)
  return headingOf(parent ?? item)
}

/** How a 차례 row reads: live while one of its items is written, quiet while none is, checked once read. */
export type RowMark = { live: boolean; todo: boolean; read: boolean }

export function rowMarkOf(
  row: ContentsRow,
  viewOf: (key: string) => ItemView,
  isRead: (key: string) => boolean,
): RowMark {
  const views = row.keys.map(viewOf)
  const live = views.some((view) => view.kind === 'writing')
  const todo = !live && !views.some(isWritten)
  const [only] = row.keys
  const read = row.keys.length === 1 && only !== undefined && isRead(only)
  return { live, todo, read }
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
 * while it shows, else the left page's. When the left page is the cover or the 차례, or the last
 * page of an item written or with nothing to write, the item starting on the right is read: a turn
 * moves two pages, so it may never sit on the left. Peeking past an item still to come is not.
 */
export function readingItemOf({ layout, first, cols, focus, viewOf }: ReadingArgs): number | null {
  if (focus !== null && visibleUnits(layout, first, cols).includes(unitOfItem(focus))) return focus
  const left = pageIn(layout, first)
  const leftItem = isItemUnit(left.unit) ? itemOfUnit(left.unit) : null
  const ended = leftItem !== null && left.n === left.of
  const done = ended && ['written', 'none'].includes(viewOf(leftItem).kind)
  if (leftItem !== null && !done) return leftItem
  if (cols < 2 || first + 1 >= layout.total) return leftItem
  const right = unitAt(layout, first + 1)
  return isItemUnit(right) && right !== left.unit ? itemOfUnit(right) : leftItem
}

/** The items on a spread and one either side, whose posts are read before a turn reaches them. */
export function itemsAround(visible: number[], count: number): number[] {
  if (visible.length === 0) return []
  const from = Math.max(0, itemOfUnit(Math.min(...visible)) - 1)
  const to = Math.min(count - 1, itemOfUnit(Math.max(...visible)) + 1)
  return Array.from({ length: Math.max(0, to - from + 1) }, (_, i) => from + i)
}

/** A page's running header: "차례" after the 차례's first page, an item's chapter on the left and its own heading on the right. */
export function runningHeadOf(layout: Layout, page: number, items: TocItem[]): string | null {
  const { unit } = pageIn(layout, page)
  const item = isItemUnit(unit) ? items[itemOfUnit(unit)] : undefined
  if (item) return page % 2 === 1 ? headingOf(item) : chapterOf(items, item)
  return unit === CONTENTS && page !== layout.starts[CONTENTS] ? '차례' : null
}

type OpenArgs = {
  items: TocItem[]
  layout: Layout | null
  first: number
  cols: number
  /** The item the reader last went to; the header keeps it while it is on the spread. */
  focus: string | null
}

/** The item the header names, null on the front spreads; before the first layout, the one gone to. */
export function openItemOf({ items, layout, first, cols, focus }: OpenArgs): string | null {
  const at = focus === null ? -1 : items.findIndex((item) => item.itemKey === focus)
  const focusUnit = at < 0 ? null : unitOfItem(at)
  const visible = layout ? visibleUnits(layout, first, cols) : []
  const unit = visible.length > 0 ? currentUnit(visible, focusUnit) : (focusUnit ?? COVER)
  return isItemUnit(unit) ? (items[itemOfUnit(unit)]?.itemKey ?? null) : null
}
