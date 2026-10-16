import { describe, expect, it } from 'vitest'
import { emptyLoginField, lockLeft } from './login'

describe('emptyLoginField', () => {
  it('names the first empty field', () => {
    expect(emptyLoginField('  ', '')).toEqual({
      field: 'loginId',
      message: '아이디를 입력해 주세요.',
    })
    expect(emptyLoginField('reader', '')).toEqual({
      field: 'password',
      message: '비밀번호를 입력해 주세요.',
    })
  })

  it('lets a filled form through, a password of spaces too', () => {
    expect(emptyLoginField('reader', '   ')).toBeNull()
  })
})

describe('lockLeft', () => {
  it('counts down in minutes and two-digit seconds', () => {
    expect(lockLeft(872)).toBe('14분 32초 남았어요.')
    expect(lockLeft(65)).toBe('1분 05초 남았어요.')
    expect(lockLeft(0)).toBe('0분 00초 남았어요.')
  })
})
