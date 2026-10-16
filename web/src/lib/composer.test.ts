import { describe, expect, it } from 'vitest'
import { type EnterKey, sendsOnEnter } from './composer'

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
