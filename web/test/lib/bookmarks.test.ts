import { describe, expect, it } from 'vitest'
import { type Bookmark, bookmarkPath, itemMarkOf, markText } from '@/lib/bookmarks'

const mark = (changes: Partial<Bookmark> = {}): Bookmark => ({
  id: 'm1',
  bookId: 'b1',
  bookTitle: '클린 코드',
  itemKey: 'k2',
  itemNumber: '02',
  itemTitle: '의미 있는 이름',
  quote: '이름은 코드의 첫 번째 설명이다.',
  createdAt: '2026-10-18T00:00:00Z',
  ...changes,
})

describe('itemMarkOf', () => {
  it("finds the item's own bookmark, not one of its phrases", () => {
    const own = mark({ id: 'own', quote: null })
    expect(itemMarkOf([mark(), own], 'k2')).toBe(own)
    expect(itemMarkOf([mark()], 'k2')).toBeUndefined()
    expect(itemMarkOf([own], null)).toBeUndefined()
  })
})

describe('markText', () => {
  it('shows the phrase kept, or the numbered title of an item kept whole', () => {
    expect(markText(mark())).toBe('이름은 코드의 첫 번째 설명이다.')
    expect(markText(mark({ quote: null }))).toBe('02 의미 있는 이름')
    expect(markText(mark({ quote: null, itemNumber: null }))).toBe('의미 있는 이름')
  })
})

describe('bookmarkPath', () => {
  it('names one bookmark under the reader’s', () => {
    expect(bookmarkPath('m1')).toBe('/api/me/bookmarks/m1')
  })
})
