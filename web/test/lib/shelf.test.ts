import { describe, expect, it } from 'vitest'
import { matching, monogramOf, placeOf, TONES, toneOf } from '@/lib/shelf'

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
