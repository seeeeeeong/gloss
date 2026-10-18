import { describe, expect, it } from 'vitest'
import { themeAttribute, themeColors, themeCookie, themeFrom } from '@/lib/theme'

describe('themeFrom', () => {
  it('takes a stored theme', () => {
    expect(themeFrom('light')).toBe('light')
    expect(themeFrom('dark')).toBe('dark')
  })

  it('follows the device for a missing or edited cookie', () => {
    for (const cookie of [undefined, '', 'sepia', 'DARK']) {
      expect(themeFrom(cookie)).toBe('system')
    }
  })

  it('reads back what it writes', () => {
    const value = themeCookie('dark').split(';')[0]?.split('=')[1]
    expect(themeFrom(value)).toBe('dark')
  })
})

describe('themeAttribute', () => {
  it('marks the root only when the reader overrides the device', () => {
    expect(themeAttribute('system')).toBeUndefined()
    expect(themeAttribute('light')).toBe('light')
  })
})

describe('themeColors', () => {
  it('follows each device scheme when the reader has not chosen', () => {
    expect(themeColors('system').map((entry) => entry.color)).toEqual(['#ffffff', '#111419'])
  })

  it('paints both schemes in the chosen theme', () => {
    expect(themeColors('light').map((entry) => entry.color)).toEqual(['#ffffff', '#ffffff'])
    expect(themeColors('dark').map((entry) => entry.color)).toEqual(['#111419', '#111419'])
  })
})
