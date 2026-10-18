'use client'

import { type RefObject, useEffect, useState } from 'react'
import { PHONE } from '@/features/reader/utils/reading'

export type StageSize = { width: number; height: number; phone: boolean }

const same = (a: StageSize | null, b: StageSize) =>
  a !== null && a.width === b.width && a.height === b.height && a.phone === b.phone

/** The stage's size and whether the window is a phone's, again on every resize; null before it is measured. */
export function useStageSize(stage: RefObject<HTMLElement | null>): StageSize | null {
  const [size, setSize] = useState<StageSize | null>(null)

  useEffect(() => {
    const el = stage.current
    if (!el) return
    const media = matchMedia(PHONE)
    const update = () => {
      const next = { width: el.clientWidth, height: el.clientHeight, phone: media.matches }
      setSize((before) => (same(before, next) ? before : next))
    }
    const observer = new ResizeObserver(update)
    observer.observe(el)
    media.addEventListener('change', update)
    update()
    return () => {
      observer.disconnect()
      media.removeEventListener('change', update)
    }
  }, [stage])

  return size
}
