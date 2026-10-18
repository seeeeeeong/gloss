import type { KeyboardEvent } from 'react'

/** The parts of a keydown the composer reads. */
export type EnterKey = {
  key: string
  shiftKey: boolean
  isComposing: boolean
  keyCode: number
}

// Taller than this the composer scrolls inside: eight lines of 26px.
export const COMPOSER_MAX_HEIGHT = 208

/**
 * Whether Enter sends. Not while a Korean syllable is still being composed (Safari reports that only
 * as keyCode 229), not with Shift, and not on a touch screen, where Enter is the only newline.
 */
export function sendsOnEnter(event: EnterKey, coarsePointer: boolean): boolean {
  return (
    event.key === 'Enter' &&
    !event.shiftKey &&
    !event.isComposing &&
    event.keyCode !== 229 &&
    !coarsePointer
  )
}

/** An Enter that only ends a syllable still being composed, as Safari's keyCode 229 says too. */
export function composingEnter(event: Omit<EnterKey, 'shiftKey'>): boolean {
  return event.key === 'Enter' && (event.isComposing || event.keyCode === 229)
}

/** For a form's keydown: that Enter must not submit it. */
export function holdImeEnter(event: KeyboardEvent) {
  const { key, keyCode } = event
  if (composingEnter({ key, keyCode, isComposing: event.nativeEvent.isComposing })) {
    event.preventDefault()
  }
}
