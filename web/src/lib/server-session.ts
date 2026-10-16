import { cookies } from 'next/headers'
import { redirect } from 'next/navigation'
import { cache } from 'react'
import { SERVER_URL } from './server-url'
import { destinationFor, HOME, type Me } from './session'

/** The signed-in reader, asked of the server with the browser's own cookies; null if none. Once a request. */
export const currentAccount = cache(async (): Promise<Me | null> => {
  const cookie = (await cookies()).toString()
  const response = await fetch(`${SERVER_URL}/api/me`, { headers: { cookie }, cache: 'no-store' })
  if (response.status === 401) return null
  if (!response.ok) throw new Error(`GET /api/me answered ${response.status}`)
  return (await response.json()) as Me
})

/** Sends the reader where they belong for this path, or returns who they are. */
export async function guard(path: string): Promise<Me | null> {
  const me = await currentAccount()
  const destination = destinationFor(me, path)
  if (destination) redirect(destination)
  return me
}

/** For pages inside the shell: a reader is signed in and done with any temporary password. */
export async function requireReader(path: string): Promise<Me> {
  const me = await guard(path)
  if (!me) redirect('/login')
  return me
}

/** A GET to the server as the signed-in reader; a lost session goes to sign-in, a refusal home. */
export async function serverGet<T>(path: string): Promise<T> {
  const cookie = (await cookies()).toString()
  const response = await fetch(`${SERVER_URL}${path}`, { headers: { cookie }, cache: 'no-store' })
  if (response.status === 401) redirect('/login')
  if (response.status === 403) redirect(HOME)
  if (!response.ok) throw new Error(`GET ${path} answered ${response.status}`)
  return (await response.json()) as T
}

/** Like serverGet, but a 404 is an answer: the thing does not exist yet. */
export async function serverFind<T>(path: string): Promise<T | null> {
  const cookie = (await cookies()).toString()
  const response = await fetch(`${SERVER_URL}${path}`, { headers: { cookie }, cache: 'no-store' })
  if (response.status === 404) return null
  if (response.status === 401) redirect('/login')
  if (response.status === 403) redirect(HOME)
  if (!response.ok) throw new Error(`GET ${path} answered ${response.status}`)
  return (await response.json()) as T
}
