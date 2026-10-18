import type { TocItem } from '../lib/books'
import {
  COVER,
  type Geometry,
  type ItemView,
  isItemUnit,
  itemOfUnit,
  type Layout,
  pageIn,
  pagerLabel,
  runningHeadOf,
} from '../lib/reader'
import { Icon } from './Icon'

type ChromeProps = {
  geo: Geometry
  layout: Layout
  first: number
  book: string
  items: TocItem[]
  viewOf: (key: string) => ItemView
}

/**
 * Running heads, the book on the left and the item on the right, and page numbers for the spread and
 * the ones either side, which a turn slides in.
 */
export function PageChrome({ geo, layout, first, book, items, viewOf }: ChromeProps) {
  const from = Math.max(0, first - geo.cols)
  const to = Math.min(layout.total, first + 2 * geo.cols)
  const pages = Array.from({ length: Math.max(0, to - from) }, (_, i) => from + i)
  return (
    <div aria-hidden="true">
      {pages.map((page) => {
        const { unit, n } = pageIn(layout, page)
        const right = page % 2 === 1
        const head = runningHeadOf(layout, page, items)
        const key = isItemUnit(unit) ? items[itemOfUnit(unit)]?.itemKey : undefined
        const live = key !== undefined && viewOf(key).kind === 'writing'
        return (
          <div key={page} className="pgc" style={{ left: page * geo.pw, width: geo.pw }}>
            {head && (
              <div className="rh" style={{ top: geo.hd, left: geo.px, right: geo.px }}>
                <span className="b">{book}</span>
                <span className="t">{head}</span>
                {live && <span className="spin" />}
                {live && <span>쓰는 중</span>}
              </div>
            )}
            {unit !== COVER && (
              <div
                className="pn"
                style={{ bottom: geo.hd, ...(right ? { right: geo.px } : { left: geo.px }) }}
              >
                {n}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

type TurnProps = {
  geo: Geometry
  layout: Layout
  first: number
  phone: boolean
  onTurn: (direction: -1 | 1) => void
}

/** The margins beside the book turn it too; on a phone a swipe does. */
export function Edges({ geo, layout, first, onTurn }: Omit<TurnProps, 'phone'>) {
  const right = geo.left + geo.cols * geo.pw - geo.px + 8
  return (
    <>
      <button
        type="button"
        className="edge"
        tabIndex={-1}
        aria-label="이전 쪽"
        disabled={first === 0}
        style={{ left: 0, top: geo.top, width: geo.left + geo.px - 8, height: geo.ph }}
        onClick={() => onTurn(-1)}
      >
        <Icon name="chevL" />
      </button>
      <button
        type="button"
        className="edge"
        tabIndex={-1}
        aria-label="다음 쪽"
        disabled={first + geo.cols >= layout.total}
        style={{ left: right, right: 0, top: geo.top, height: geo.ph }}
        onClick={() => onTurn(1)}
      >
        <Icon name="chevR" />
      </button>
    </>
  )
}

/** Under the book: the left page's number in its item, "차례" on the front spreads. */
export function Pager({ geo, layout, first, phone, onTurn }: TurnProps) {
  return (
    <div className="pager" style={{ top: geo.top + geo.ph + (phone ? 4 : 6) }}>
      <button
        type="button"
        className="ibtn sm"
        aria-label="이전 쪽"
        disabled={first === 0}
        onClick={() => onTurn(-1)}
      >
        <Icon name="arrowL" size={18} />
      </button>
      <span className="pg-t">{pagerLabel(layout, first)}</span>
      <button
        type="button"
        className="ibtn sm"
        aria-label="다음 쪽"
        disabled={first + geo.cols >= layout.total}
        onClick={() => onTurn(1)}
      >
        <Icon name="arrowR" size={18} />
      </button>
    </div>
  )
}
