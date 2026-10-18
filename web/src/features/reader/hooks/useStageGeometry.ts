'use client'

import { type RefObject, useEffect, useMemo, useState } from 'react'
import { useStageSize } from '@/features/reader/hooks/useStageSize'
import { geometryOf } from '@/features/reader/utils/reader'

/**
 * The pages that fit the stage at the [line] of the reading size, again on every resize; a web font
 * arriving re-renders to reflow them.
 */
export function useStageGeometry(stage: RefObject<HTMLElement | null>, line: number) {
  const size = useStageSize(stage)
  const [, setFonts] = useState(0)
  const geo = useMemo(
    () => (size ? geometryOf(size.width, size.height, size.phone, line) : null),
    [size, line],
  )

  useEffect(() => {
    const fonts = document.fonts
    const reflow = () => setFonts((n) => n + 1)
    fonts.addEventListener('loadingdone', reflow)
    void fonts.ready.then(reflow)
    return () => fonts.removeEventListener('loadingdone', reflow)
  }, [])

  return { geo, phone: size?.phone ?? false, width: size?.width ?? 0 }
}
