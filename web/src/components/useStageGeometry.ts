'use client'

import { type RefObject, useEffect, useState } from 'react'
import { type Geometry, geometryOf } from '@/lib/reader'

// As in globals.css.
const PHONE = '(max-width: 767px)'

const same = (a: Geometry | null, b: Geometry) =>
  a !== null && (Object.keys(b) as (keyof Geometry)[]).every((k) => a[k] === b[k])

/** The pages that fit the stage, again on every resize; a web font arriving re-renders to reflow them. */
export function useStageGeometry(stage: RefObject<HTMLElement | null>) {
  const [geo, setGeo] = useState<Geometry | null>(null)
  const [phone, setPhone] = useState(false)
  const [, setFonts] = useState(0)

  useEffect(() => {
    const el = stage.current
    if (!el) return
    const media = matchMedia(PHONE)
    const update = () => {
      setPhone(media.matches)
      const next = geometryOf(el.clientWidth, el.clientHeight, media.matches)
      setGeo((before) => (same(before, next) ? before : next))
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

  useEffect(() => {
    const fonts = document.fonts
    const reflow = () => setFonts((n) => n + 1)
    fonts.addEventListener('loadingdone', reflow)
    void fonts.ready.then(reflow)
    return () => fonts.removeEventListener('loadingdone', reflow)
  }, [])

  return { geo, phone }
}
