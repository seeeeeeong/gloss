'use client'

import { useEffect, useRef, useState } from 'react'

type Tip = { text: string; left: number; top: number; above: number }

// Kept off the window's edges.
const EDGE = 8

/** One tooltip for every `data-tip` control, under it on hover or keyboard focus; none on touch. */
export function Tooltip() {
  const [tip, setTip] = useState<Tip | null>(null)
  const box = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const coarse = matchMedia('(pointer: coarse)')
    function show(target: EventTarget | null, keyboard: boolean) {
      const el = target instanceof Element ? target.closest<HTMLElement>('[data-tip]') : null
      if (!el?.dataset.tip || coarse.matches || (keyboard && !el.matches(':focus-visible'))) {
        setTip(null)
        return
      }
      const rect = el.getBoundingClientRect()
      setTip({
        text: el.dataset.tip,
        left: rect.left + rect.width / 2,
        top: rect.bottom + 8,
        above: rect.top - 8,
      })
    }
    const over = (event: MouseEvent) => show(event.target, false)
    const focus = (event: FocusEvent) => show(event.target, true)
    const hide = () => setTip(null)
    document.addEventListener('mouseover', over)
    document.addEventListener('focusin', focus)
    document.addEventListener('click', hide)
    return () => {
      document.removeEventListener('mouseover', over)
      document.removeEventListener('focusin', focus)
      document.removeEventListener('click', hide)
    }
  }, [])

  // Centred under the control once its size is known, or over it when the window ends first.
  useEffect(() => {
    const el = box.current
    if (!el || !tip) return
    const width = el.offsetWidth
    const left = Math.max(EDGE, Math.min(innerWidth - width - EDGE, tip.left - width / 2))
    el.style.left = `${left}px`
    if (tip.top + el.offsetHeight > innerHeight - EDGE)
      el.style.top = `${tip.above - el.offsetHeight}px`
  }, [tip])

  if (!tip) return null
  return (
    <div ref={box} className="tip" role="tooltip" style={{ top: tip.top, left: tip.left }}>
      {tip.text}
    </div>
  )
}
