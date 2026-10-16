import { describe, expect, it } from 'vitest'
import { checkPasswordChange, passwordFieldOf } from './password'

const filled = {
  current: 'old password',
  next: 'a new and long one',
  confirm: 'a new and long one',
}

describe('checkPasswordChange', () => {
  it('passes a filled form whose new pair agrees', () => {
    expect(checkPasswordChange(filled, 'settings')).toEqual({})
  })

  it('asks for the current password by the name each place gives it', () => {
    expect(checkPasswordChange({ ...filled, current: '' }, 'settings').current).toBe(
      '현재 비밀번호를 입력해 주세요.',
    )
    expect(checkPasswordChange({ ...filled, current: '' }, 'first').current).toBe(
      '임시 비밀번호를 입력해 주세요.',
    )
  })

  it('counts characters, not UTF-16 units, toward the ten', () => {
    const nine = '📚'.repeat(9)
    expect(checkPasswordChange({ ...filled, next: nine, confirm: nine }, 'first').next).toBe(
      '10자 이상으로 정해 주세요.',
    )
  })

  it('says the pair differs only once the new one is long enough', () => {
    expect(checkPasswordChange({ ...filled, confirm: 'something else' }, 'settings')).toEqual({
      confirm: '새 비밀번호가 서로 달라요.',
    })
    expect(checkPasswordChange({ ...filled, confirm: 'something else' }, 'first')).toEqual({
      confirm: '비밀번호가 서로 달라요.',
    })
    expect(checkPasswordChange({ ...filled, next: 'short', confirm: 'x' }, 'first')).toEqual({
      next: '10자 이상으로 정해 주세요.',
    })
  })
})

describe('passwordFieldOf', () => {
  it('puts a refusal under the field it is about', () => {
    expect(passwordFieldOf('account.wrong_password')).toBe('current')
    expect(passwordFieldOf('account.password_common')).toBe('next')
    expect(passwordFieldOf('account.password_change_required')).toBeNull()
    expect(passwordFieldOf('common.network')).toBeNull()
  })
})
