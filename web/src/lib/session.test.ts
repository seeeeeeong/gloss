import { describe, expect, it } from 'vitest'
import { destinationFor } from './session'

const me = (overrides = {}) => ({
  id: '1',
  loginId: 'reader',
  displayName: 'Reader',
  role: 'member',
  mustChangePassword: false,
  lastLoginAt: null,
  ...overrides,
})

describe('destinationFor', () => {
  it('sends someone signed out to sign in', () => {
    expect(destinationFor(null, '/me')).toBe('/login')
  })

  it('keeps someone with a temporary password on the password page', () => {
    expect(destinationFor(me({ mustChangePassword: true }), '/')).toBe('/password')
    expect(destinationFor(me({ mustChangePassword: true }), '/password')).toBeNull()
  })

  it('lets everyone else stay', () => {
    expect(destinationFor(me(), '/me')).toBeNull()
  })

  it('sends a signed-in reader away from the sign-in and password pages to a new book', () => {
    expect(destinationFor(me(), '/login')).toBe('/')
    expect(destinationFor(me(), '/password')).toBe('/')
  })

  it('keeps the operator pages to operators', () => {
    expect(destinationFor(me(), '/admin/accounts')).toBe('/')
    expect(destinationFor(me({ role: 'operator' }), '/admin/accounts')).toBeNull()
  })
})
