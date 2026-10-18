import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { ReaderMenu } from '@/components/ReaderMenu'
import type { ItemView, MenuRow } from '@/lib/reader'

const rows: MenuRow[] = [
  { key: 'c1', label: '1장 서론', heading: true },
  { key: 's11', label: '1.1 할 일', heading: false },
  { key: 's12', label: '1.2 구성', heading: false },
]
const menu = (current: string | null) =>
  renderToStaticMarkup(
    <ReaderMenu
      bookId="b1"
      rows={rows}
      current={current}
      viewOf={(): ItemView => ({ kind: 'todo' })}
      left={null}
      focusCurrent
      onGo={() => {}}
      onClose={() => {}}
    />,
  )
const starts = (out: string) => [...out.matchAll(/<a [^>]*tabindex="0"[^>]*>/g)].map((m) => m[0])

describe('ReaderMenu', () => {
  it('starts the keyboard on the current row', () => {
    const [start, ...more] = starts(menu('s12'))
    expect(more).toEqual([])
    expect(start).toContain('aria-current="page"')
    expect(start).toContain('item=s12')
  })

  it('starts on the first row when nothing is open, on the cover or the 차례', () => {
    const [start, ...more] = starts(menu(null))
    expect(more).toEqual([])
    expect(start).toContain('item=c1')
  })
})
