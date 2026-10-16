import { describe, expect, it } from 'vitest'
import {
  authorsOf,
  type BookCandidate,
  bylineOf,
  contentsHref,
  contentsSectionsOf,
  creditOf,
  editionOf,
  isBookId,
  landingOf,
  searchPath,
  type Toc,
  type TocItem,
  tocStatusLabel,
  treeOf,
} from './books'

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

describe('creditOf', () => {
  it('names the source of a book found in one', () => {
    expect(creditOf('yes24')).toBe('출처 YES24')
  })

  it('credits no one for a book typed in', () => {
    expect(creditOf('manual')).toBeNull()
  })
})

describe('editionOf', () => {
  const candidate: BookCandidate = {
    ref: '101',
    source: 'yes24',
    url: 'https://www.yes24.com/product/goods/101',
    title: '오브젝트',
    authors: ['조영호 저'],
    publisher: '위키북스',
    isbn13: '9788966262472',
    publishedOn: '2019-06-17',
    kind: '도서',
    hasContents: true,
  }

  it('tells an edition by who wrote it, its publisher, its year and its kind', () => {
    expect(editionOf(candidate)).toBe('조영호 저 · 위키북스 · 2019 · 도서')
  })

  it('leaves out what the source did not say', () => {
    expect(editionOf({ ...candidate, publisher: null, publishedOn: null, kind: null })).toBe(
      '조영호 저',
    )
  })
})

describe('searchPath', () => {
  it('carries the words as one query value', () => {
    expect(searchPath('Real MySQL 8.0 & more')).toBe(
      '/api/me/books/search?query=Real%20MySQL%208.0%20%26%20more',
    )
  })
})

const tocOf = (id: string, versionNo: number, status = 'confirmed'): Toc => ({
  id,
  versionNo,
  origin: 'typed',
  status,
  confirmedAt: null,
  raw: '',
  items: [],
})

describe('landingOf', () => {
  it('opens a confirmed book in the reader and an unconfirmed one at its review', () => {
    expect(landingOf({ currentTocId: 't1' })).toBe('reader')
    expect(landingOf({ currentTocId: null })).toBe('review')
    expect(contentsHref('b1')).toBe('/books/b1/contents')
  })
})

describe('contentsSectionsOf', () => {
  it('reviews the only draft of a book not yet confirmed', () => {
    const draft = tocOf('t1', 1, 'draft')
    expect(contentsSectionsOf(null, draft)).toEqual([
      { id: 'draft', title: '목차', toc: draft, review: true },
    ])
  })

  it('edits the version in use when nothing newer waits', () => {
    const inUse = tocOf('t1', 1)
    expect(contentsSectionsOf(inUse, inUse)).toEqual([
      { id: 'toc', title: '목차', toc: inUse, review: true },
    ])
  })

  it('shows the version in use and reviews the newer draft beside it', () => {
    const inUse = tocOf('t1', 1)
    const draft = tocOf('t2', 2, 'draft')
    expect(contentsSectionsOf(inUse, draft)).toEqual([
      { id: 'toc', title: '목차', toc: inUse, review: false },
      { id: 'draft', title: '새 목차', toc: draft, review: true },
    ])
  })
})
