'use client'

import { type RefObject, useLayoutEffect, useRef, useState } from 'react'
import {
  type Anchor,
  align,
  anchorAt,
  type Geometry,
  type Layout,
  pageOfAnchor,
  pagerLabel,
  sameLayout,
} from '@/lib/reader'

const REDUCED = '(prefers-reduced-motion: reduce)'
const moving = () => !matchMedia(REDUCED).matches

/** The page each `[data-unit]` in the flow starts on, one column a page. */
function measure(flow: HTMLElement, pw: number): Layout {
  const origin = flow.getBoundingClientRect().left
  const pageAt = (rect: DOMRect) => Math.max(0, Math.floor((rect.left - origin + 2) / pw))
  const units = [...flow.querySelectorAll<HTMLElement>('[data-unit]')]
  const starts = units.map((unit) =>
    pageAt(unit.getClientRects()[0] ?? unit.getBoundingClientRect()),
  )
  const rects = units.at(-1)?.getClientRects()
  const end = rects?.[rects.length - 1]
  const last = Math.max(end ? pageAt(end) : 0, starts.at(-1) ?? 0)
  return { starts, total: last + 1 }
}

/**
 * Which spread of the flow is open. The reader's place is an anchor, a unit and a page in it, so
 * a resize or a post being written keeps them where they were; turns slide unless motion is
 * reduced. [settled] is true once the open spread is the anchor's on the current layout.
 */
export function useSpreads(
  flow: RefObject<HTMLElement | null>,
  geo: Geometry | null,
  start: Anchor,
) {
  const [layout, setLayout] = useState<Layout | null>(null)
  const [first, setFirst] = useState(0)
  const [slide, setSlide] = useState(false)
  const [drag, setDrag] = useState(0)
  const [announced, setAnnounced] = useState('')
  const settled = useRef(false)
  const anchor = useRef(start)
  const cols = geo?.cols ?? 1

  // After every render: where each unit starts now, and the spread that keeps the reader's place.
  // Both change in one render, so no spread in between is taken for one the reader arrived at.
  useLayoutEffect(() => {
    const el = flow.current
    if (!geo || !el) return
    const measured = measure(el, geo.pw)
    const moved = !sameLayout(layout, measured)
    const target = align(pageOfAnchor(measured, anchor.current), geo.cols, measured.total)
    settled.current = !moved && target === first
    if (moved) setLayout(measured)
    if (target !== first) {
      setSlide(false)
      setFirst(target)
    }
  })

  function go(page: number, to: Anchor, animate: boolean) {
    if (!layout) return
    anchor.current = to
    setSlide(animate && moving())
    setDrag(0)
    setFirst(page)
    setAnnounced(pagerLabel(layout, page))
  }

  function springBack() {
    setSlide(moving())
    setDrag(0)
  }

  /** One spread on or back; false at either end, where a drag springs back. */
  function turn(direction: -1 | 1): boolean {
    const page = layout ? align(first + direction * cols, cols, layout.total) : first
    if (!layout || page === first) {
      springBack()
      return false
    }
    go(page, anchorAt(layout, page), true)
    return true
  }

  /** Opens a unit at its first page, sliding when it is the next spread or the one before. */
  function open(unit: number) {
    if (!layout) return
    const page = align(layout.starts[unit] ?? 0, cols, layout.total)
    go(page, { unit, offset: 0 }, Math.abs(page - first) === cols)
  }

  /** Opens the spread holding [page] at once, as focus moving there does. */
  function show(page: number) {
    if (!layout || (page >= first && page < first + cols)) return false
    const spread = align(page, cols, layout.total)
    go(spread, anchorAt(layout, spread), false)
    return true
  }

  function follow(dx: number) {
    setSlide(false)
    setDrag(dx)
  }

  return {
    layout,
    first,
    cols,
    slide,
    drag,
    announced,
    settled,
    turn,
    open,
    show,
    follow,
    springBack,
  }
}
