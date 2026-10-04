export type Me = {
  id: string
  loginId: string
  displayName: string
  role: 'operator' | 'member' | string
  mustChangePassword: boolean
}

const OPEN_WHEN_SIGNED_OUT = ['/login']

/** Where a reader on this path should be sent instead, or null to stay. */
export function destinationFor(me: Me | null, path: string): string | null {
  if (me === null) return OPEN_WHEN_SIGNED_OUT.includes(path) ? null : '/login'
  if (me.mustChangePassword) return path === '/password' ? null : '/password'
  if (path === '/login' || path === '/password') return '/'
  if (path.startsWith('/admin') && me.role !== 'operator') return '/'
  return null
}
