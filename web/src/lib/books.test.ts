import { describe, expect, it } from 'vitest'
import { authorsOf, bylineOf, isBookId, type TocItem, tocStatusLabel, treeOf } from './books'

const item = (id: string, parentId: string | null, title = id): TocItem => ({
  id,
  parentId,
  depth: 1,
  number: null,
  title,
  itemKey: title,
})

describe('authorsOf', () => {
  it('splits on commas, Korean ones included, and drops blanks', () => {
    expect(authorsOf(' 김철수, 이영희，박민수、 ,')).toEqual(['김철수', '이영희', '박민수'])
  })

  it('makes no authors of an empty field', () => {
    expect(authorsOf('   ')).toEqual([])
  })
})

describe('bylineOf', () => {
  it('joins the authors and the publisher', () => {
    expect(bylineOf({ authors: ['A', 'B'], publisher: '한빛미디어' })).toBe('A, B · 한빛미디어')
  })

  it('leaves out what is missing', () => {
    expect(bylineOf({ authors: [], publisher: '한빛미디어' })).toBe('한빛미디어')
    expect(bylineOf({ authors: ['A'], publisher: null })).toBe('A')
    expect(bylineOf({ authors: [], publisher: null })).toBe('')
  })
})

describe('treeOf', () => {
  it('nests each item under its parent, in reading order', () => {
    const tree = treeOf([
      item('1', null),
      item('1.1', '1'),
      item('1.1.1', '1.1'),
      item('1.2', '1'),
      item('2', null),
    ])
    const [first, second] = tree
    expect(tree.map((node) => node.id)).toEqual(['1', '2'])
    expect(first?.children.map((node) => node.id)).toEqual(['1.1', '1.2'])
    expect(first?.children[0]?.children.map((node) => node.id)).toEqual(['1.1.1'])
    expect(second?.children).toEqual([])
  })

  it('keeps an item whose parent is missing at the top, rather than losing it', () => {
    expect(treeOf([item('orphan', 'gone')]).map((node) => node.id)).toEqual(['orphan'])
  })
})

describe('isBookId', () => {
  it('takes a UUID and nothing else', () => {
    expect(isBookId('0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b')).toBe(true)
    expect(isBookId('new')).toBe(false)
    expect(isBookId('0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b/x')).toBe(false)
  })
})

describe('tocStatusLabel', () => {
  it('names statuses in Korean, and shows an unknown one as it came', () => {
    expect(tocStatusLabel('draft')).toBe('확정 전')
    expect(tocStatusLabel('confirmed')).toBe('확정')
    expect(tocStatusLabel('archived')).toBe('archived')
  })
})
