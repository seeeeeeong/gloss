import { describe, expect, it } from 'vitest'
import { composingEnter, type EnterKey, sendsOnEnter } from '@/lib/composer'

const enter: EnterKey = { key: 'Enter', shiftKey: false, isComposing: false, keyCode: 13 }

describe('sendsOnEnter', () => {
  it('sends on a plain Enter with a mouse or trackpad', () => {
    expect(sendsOnEnter(enter, false)).toBe(true)
  })

  it('waits while a Korean syllable is being composed', () => {
    expect(sendsOnEnter({ ...enter, isComposing: true }, false)).toBe(false)
    expect(sendsOnEnter({ ...enter, keyCode: 229 }, false)).toBe(false)
  })

  it('leaves Shift+Enter and a touch screen to start a new line', () => {
    expect(sendsOnEnter({ ...enter, shiftKey: true }, false)).toBe(false)
    expect(sendsOnEnter(enter, true)).toBe(false)
  })

  it('ignores other keys', () => {
    expect(sendsOnEnter({ ...enter, key: 'a', keyCode: 65 }, false)).toBe(false)
  })
})

describe('composingEnter', () => {
  it('catches only an Enter that ends a syllable', () => {
    expect(composingEnter({ key: 'Enter', isComposing: true, keyCode: 13 })).toBe(true)
    expect(composingEnter({ key: 'Enter', isComposing: false, keyCode: 229 })).toBe(true)
    expect(composingEnter({ key: 'Enter', isComposing: false, keyCode: 13 })).toBe(false)
    expect(composingEnter({ key: 'a', isComposing: true, keyCode: 229 })).toBe(false)
  })
})
