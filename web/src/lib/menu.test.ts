import { describe, expect, it } from 'vitest'
import { menuStep } from './menu'

describe('menuStep', () => {
  it('moves down and up, wrapping at either end', () => {
    expect(menuStep('ArrowDown', 0, 3)).toBe(1)
    expect(menuStep('ArrowDown', 2, 3)).toBe(0)
    expect(menuStep('ArrowUp', 0, 3)).toBe(2)
  })

  it('starts at the near end when nothing in the menu has focus', () => {
    expect(menuStep('ArrowDown', -1, 3)).toBe(0)
    expect(menuStep('ArrowUp', -1, 3)).toBe(2)
  })

  it('jumps to the first and last, and leaves other keys alone', () => {
    expect(menuStep('Home', 2, 3)).toBe(0)
    expect(menuStep('End', 0, 3)).toBe(2)
    expect(menuStep('Enter', 0, 3)).toBeNull()
    expect(menuStep('ArrowDown', -1, 0)).toBeNull()
  })
})
