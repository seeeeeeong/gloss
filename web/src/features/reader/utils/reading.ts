/** How large the book's text is set: three sizes, chosen from the reader, the settings or 마이페이지. */
export const READING_SIZES = [16, 18, 20] as const
export type ReadingSize = (typeof READING_SIZES)[number]

export const DEFAULT_SIZE: ReadingSize = 18

// A line about 1.8 times the text, in whole pixels, so a page holds whole lines at every size.
const LINES: Record<ReadingSize, number> = { 16: 28, 18: 32, 20: 36 }
const LABELS: Record<ReadingSize, string> = { 16: '작게', 18: '보통', 20: '크게' }

export const lineOf = (size: ReadingSize) => LINES[size]
export const sizeLabel = (size: ReadingSize) => LABELS[size]

/** The size kept in storage; anything else, or nothing, is the default. */
export function sizeFrom(stored: string | null): ReadingSize {
  const value = Number(stored)
  return READING_SIZES.find((size) => size === value) ?? DEFAULT_SIZE
}

/** One size smaller or larger, staying within the three. */
export function stepped(size: ReadingSize, by: -1 | 1): ReadingSize {
  const at = Math.min(READING_SIZES.length - 1, Math.max(0, READING_SIZES.indexOf(size) + by))
  return READING_SIZES[at] ?? size
}

// As in globals.css.
export const PHONE = '(max-width: 767px)'
