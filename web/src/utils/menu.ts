import type { KeyboardEvent } from 'react'

/** The item a key moves to in a menu of [count], from [index] (-1 for none); null for other keys. */
export function menuStep(key: string, index: number, count: number): number | null {
  if (count === 0) return null
  if (key === 'Home') return 0
  if (key === 'End') return count - 1
  if (key === 'ArrowDown') return index < 0 ? 0 : (index + 1) % count
  if (key === 'ArrowUp') return index < 0 ? count - 1 : (index - 1 + count) % count
  return null
}

/** For a menu's keydown: arrow keys, Home and End move focus among its enabled items. */
export function moveInMenu(event: KeyboardEvent<HTMLElement>) {
  const items = [
    ...event.currentTarget.querySelectorAll<HTMLElement>('[role^="menuitem"]:not(:disabled)'),
  ]
  const next = menuStep(
    event.key,
    items.indexOf(document.activeElement as HTMLElement),
    items.length,
  )
  if (next === null) return
  event.preventDefault()
  items[next]?.focus()
}
