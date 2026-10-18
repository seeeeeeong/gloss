import { describe, expect, it } from 'vitest'
import {
  matching,
  monogramOf,
  placeOf,
  type RecentRead,
  readsFrom,
  TONES,
  toneOf,
  withRead,
} from '@/lib/shelf'

const read = (bookId: string, itemKey = 'a'): RecentRead => ({
  bookId,
  itemKey,
  title: '프로세스의 이해',
  position: 3,
  total: 8,
})

describe('toneOf', () => {
  it('gives a book the same tone every time, from the four', () => {
    const id = '0199a3b2-7c4e-7d10-9b1f-2a3c4d5e6f70'
    expect(toneOf(id)).toBe(toneOf(id))
    expect(TONES).toContain(toneOf(id))
  })
})

describe('monogramOf', () => {
  it('takes the first letter of the title, whole', () => {
    expect(monogramOf('  운영체제')).toBe('운')
    expect(monogramOf('Real MySQL 8.0')).toBe('R')
    expect(monogramOf('𝕏 책')).toBe('𝕏')
    expect(monogramOf('')).toBe('')
  })
})

describe('withRead', () => {
  it('puts the latest read in front and keeps one per book', () => {
    const reads = withRead([read('b'), read('a', 'x')], read('a', 'y'))
    expect(reads.map((kept) => [kept.bookId, kept.itemKey])).toEqual([
      ['a', 'y'],
      ['b', 'a'],
    ])
  })

  it('keeps twenty books at most', () => {
    let reads: RecentRead[] = []
    for (let i = 0; i < 25; i++) reads = withRead(reads, read(`book-${i}`))
    expect(reads).toHaveLength(20)
    expect(reads[0]?.bookId).toBe('book-24')
  })
})

describe('readsFrom', () => {
  it('reads what was kept and drops what is not a read', () => {
    const stored = JSON.stringify([read('a'), { bookId: 'b' }, 'x'])
    expect(readsFrom(stored)).toEqual([read('a')])
  })

  it('reads nothing from nothing or from a broken value', () => {
    expect(readsFrom(null)).toEqual([])
    expect(readsFrom('{')).toEqual([])
    expect(readsFrom('{"bookId":"a"}')).toEqual([])
  })
})

describe('placeOf', () => {
  it('writes the place with two digits on each side', () => {
    expect(placeOf({ position: 3, total: 8 })).toBe('03 / 08')
    expect(placeOf({ position: 12, total: 120 })).toBe('12 / 120')
  })
})

describe('matching', () => {
  const books = [
    { title: '운영체제', authors: ['Abraham Silberschatz'] },
    { title: '클린 코드', authors: ['Robert C. Martin'] },
  ]

  it('finds a book by its title or an author, ignoring case and spaces around the query', () => {
    expect(matching(books, ' 운영 ')).toEqual([books[0]])
    expect(matching(books, 'martin')).toEqual([books[1]])
    expect(matching(books, '')).toEqual(books)
    expect(matching(books, '없는 책')).toEqual([])
  })
})
