import { describe, expect, it } from 'vitest'
import type { TocItem } from './books'
import { headingOf, placeOf, postHref, postPath } from './posts'

const item = (itemKey: string, number: string | null = null): TocItem => ({
  id: itemKey,
  parentId: null,
  depth: 1,
  number,
  title: itemKey,
  itemKey,
})

describe('postHref and postPath', () => {
  it('carry the key in the query, encoded, so a slash or a space in a title is safe', () => {
    const key = '1장 객체 > 1.1 tcp/ip & 소켓'
    const encoded =
      '1%EC%9E%A5%20%EA%B0%9D%EC%B2%B4%20%3E%201.1%20tcp%2Fip%20%26%20%EC%86%8C%EC%BC%93'
    expect(postHref('b1', key)).toBe(`/books/b1/post?item=${encoded}`)
    expect(postPath('b1', key)).toBe(`/api/me/books/b1/post?itemKey=${encoded}`)
  })
})

describe('headingOf', () => {
  it('puts the number before the title when there is one', () => {
    expect(headingOf({ number: '1.2', title: '무엇이 문제인가' })).toBe('1.2 무엇이 문제인가')
    expect(headingOf({ number: null, title: '들어가며' })).toBe('들어가며')
  })
})

describe('placeOf', () => {
  const items = [item('a'), item('b'), item('c')]

  it('finds a section and its neighbors in reading order', () => {
    const place = placeOf(items, 'b')
    expect(place?.item.itemKey).toBe('b')
    expect(place?.previous?.itemKey).toBe('a')
    expect(place?.next?.itemKey).toBe('c')
  })

  it('has no neighbor past either end', () => {
    expect(placeOf(items, 'a')?.previous).toBeNull()
    expect(placeOf(items, 'c')?.next).toBeNull()
  })

  it('finds nothing for a key the contents do not have', () => {
    expect(placeOf(items, 'z')).toBeNull()
  })
})
