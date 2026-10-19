import { describe, expect, it } from 'vitest'
import { crumbOf, initialOf } from '@/app/(app)/_components/shell'

describe('crumbOf', () => {
  it('puts the shelf screens under the shelf and the account pages on their own', () => {
    expect(crumbOf('/')).toEqual({ shelf: true, name: '내 서재' })
    expect(crumbOf('/books/new')).toEqual({ shelf: true, name: '책 추가' })
    expect(crumbOf('/books/new/0199a3b2')).toEqual({ shelf: true, name: '책 추가' })
    expect(crumbOf('/bookmarks')).toEqual({ shelf: true, name: '책갈피' })
    expect(crumbOf('/me')).toEqual({ shelf: false, name: '마이페이지' })
    expect(crumbOf('/admin/accounts')).toEqual({ shelf: false, name: '계정 관리' })
  })

  it('leaves the header to the reader, which titles itself', () => {
    expect(crumbOf('/books/0199a3b2')).toBeNull()
    expect(crumbOf('/administer')).toBeNull()
  })
})

describe('initialOf', () => {
  it('takes the first letter of the name, upper-cased', () => {
    expect(initialOf('김민준')).toBe('김')
    expect(initialOf(' seeeeeeong')).toBe('S')
    expect(initialOf('')).toBe('')
  })
})
