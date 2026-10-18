import { describe, expect, it } from 'vitest'
import { generationPath, headingOf, isUnderway, postPath, readerHref } from '@/lib/posts'

describe('readerHref and postPath', () => {
  it('carry the key in the query, encoded, so a slash or a space in a title is safe', () => {
    const key = '1장 객체 > 1.1 tcp/ip & 소켓'
    const encoded =
      '1%EC%9E%A5%20%EA%B0%9D%EC%B2%B4%20%3E%201.1%20tcp%2Fip%20%26%20%EC%86%8C%EC%BC%93'
    expect(readerHref('b1', key)).toBe(`/books/b1?item=${encoded}`)
    expect(postPath('b1', key)).toBe(`/api/me/books/b1/post?itemKey=${encoded}`)
  })
})

describe('generationPath', () => {
  it('carries the key encoded, and asks for a retry only when told to', () => {
    expect(generationPath('b1', '1.1 tcp/ip')).toBe(
      '/api/me/books/b1/post/generation?itemKey=1.1%20tcp%2Fip',
    )
    expect(generationPath('b1', 'a', true)).toBe(
      '/api/me/books/b1/post/generation?itemKey=a&retry=true',
    )
  })
})

describe('a generation state', () => {
  it('is asked about again only while the plan or the post is being written', () => {
    expect(['planning', 'writing'].map(isUnderway)).toEqual([true, true])
    expect(['ready', 'none', 'failed', 'later'].map(isUnderway)).toEqual([
      false,
      false,
      false,
      false,
    ])
  })
})

describe('headingOf', () => {
  it('puts the number before the title when there is one', () => {
    expect(headingOf({ number: '1.2', title: '무엇이 문제인가' })).toBe('1.2 무엇이 문제인가')
    expect(headingOf({ number: null, title: '들어가며' })).toBe('들어가며')
  })
})
