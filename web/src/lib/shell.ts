/** Where a screen sits, shown in the header: under the shelf, or on its own like the account pages. */
export type Crumb = { shelf: boolean; name: string }

const CONTENTS = /^\/books\/[^/]+\/contents$/

/** The header's crumb for [path]; null where the screen puts its own title there, as the reader does. */
export function crumbOf(path: string): Crumb | null {
  if (path === '/') return { shelf: true, name: '내 서재' }
  if (path === '/books/new') return { shelf: true, name: '책 추가' }
  if (CONTENTS.test(path)) return { shelf: true, name: '목차' }
  if (path === '/me') return { shelf: false, name: '마이페이지' }
  if (path === '/admin' || path.startsWith('/admin/')) return { shelf: false, name: '계정 관리' }
  return null
}

/** The letter in the header's round button: the reader's name's first. */
export const initialOf = (displayName: string) =>
  (Array.from(displayName.trim())[0] ?? '').toUpperCase()
